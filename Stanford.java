import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.StringReader;

import spark.Spark;
import com.google.gson.Gson;

import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.parser.nndep.DependencyParser;

public class Stanford {
    static String BEST_STRATEGY = "PCFG.maxent.posTags";
    static String BEST_DEPENDENCY = "NNDEP.posTags";
    static String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
    static String taggerPath = "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger";
    static LexicalizedParser parser = null;
    static MaxentTagger tagger = null;
    static DependencyParser dependencyParser = null;

    private static synchronized void initializeParsers() {
        if (null == parser) {
            parser = LexicalizedParser.loadModel(parserModel);
            System.out.println("LexicalizedParser ready.");
        }
        if (null == tagger) {
            tagger = new MaxentTagger(taggerPath);
            System.out.println("MaxentTagger ready.");
       }
        if (null == dependencyParser) {
            if (!"0".equals(System.getenv("STANFORD_DEPENDENCY_PARSER"))) {
                dependencyParser = DependencyParser.loadFromModelFile(DependencyParser.DEFAULT_MODEL);
                System.out.println("DependencyParser ready.");
            } else {
                System.out.println("DependencyParser disabled.");
            }
        }
    }

    public static void main(String[] args) {
        Spark.port(Integer.valueOf(System.getenv("STANFORD_PORT")));
        Spark.staticFileLocation("/public");

        Spark.get("/tree/:text", (request, response) -> {
            initializeParsers();
            Gson gson = new Gson();
            String sentence = request.params(":text");
            ParseResult parseResult = parseText(sentence);

            String json = gson.toJson(parseResult);
            System.out.println("parse result " + json);

            return (json);
        });

        Spark.post("/tree/", (request, response) -> {
            initializeParsers();
            Gson gson = new Gson();
            String body = request.body();
            ParseRequest parseRequest = gson.fromJson(body, ParseRequest.class);
            System.out.println(parseRequest);

            String sentence = parseRequest.text;
            String[] posTags = parseRequest.posTags;
            ParseResult parseResult = parseText(sentence);
            parseResult.strategy = "PCFG";
            ParseResult lowerResult = parseText(sentence.toLowerCase());
            parseResult.strategy = "PCFG.lowercase";

            ParseResponse parseResponse = new ParseResponse();
            parseResponse.tree = parseResult.tree;
            parseResponse.dependencies = parseResult.dependencies;
            parseResponse.strategy = parseResult.strategy;

            ParseResult[] simpleAlternates = new ParseResult[] { parseResult, lowerResult };
            ParseResult[] otherAlternates = parseTextAlternates(sentence, posTags);
            if (otherAlternates.length > 0) {
                //This is without the custom overlay tags, this may not be desired
                parseResponse.tags = otherAlternates[0].tags;
                for (ParseResult otherResult: otherAlternates) {
                    if (otherResult.strategy.equals(BEST_STRATEGY)) {
                        parseResponse.tree = otherResult.tree;
                        parseResponse.strategy = otherResult.strategy;
                    }
                    if (otherResult.strategy.equals(BEST_DEPENDENCY)) {
                        parseResponse.dependencies = otherResult.dependencies;
                    }
                }
            }
            parseResponse.alternates = Stream.concat(
                    Arrays.stream(simpleAlternates),
                    Arrays.stream(otherAlternates)).toArray(ParseResult[]::new);
            String json = gson.toJson(parseResponse);
            System.out.println(json);

            return (json);
        });

    }

    public synchronized static ParseResult parseText(String text) {
        ParseResult parseResult = new ParseResult();
        System.out.println("parseText will parse: " + text);

        Tree tree = parser.parse(text);
        String treeString = tree.toString();
        TreebankLanguagePack treeLanguagePack = new PennTreebankLanguagePack();
        GrammaticalStructureFactory structureFactory = treeLanguagePack.grammaticalStructureFactory();
        GrammaticalStructure grammaticalStructure = structureFactory.newGrammaticalStructure(tree);
        List<TypedDependency> typedDependencies = grammaticalStructure.typedDependenciesCCprocessed();
        String dependenciesString = typedDependencies.toString();
        parseResult.tree = treeString;
        parseResult.dependencies = dependenciesString;

        return parseResult;
    }

    public synchronized static ParseResult[] parseTextAlternates(String text, String[] posTags) {
        ArrayList<ParseResult> parseResults = new ArrayList<ParseResult>();
        ParseResult parseResult;

        DocumentPreprocessor tokenizer = new DocumentPreprocessor(new StringReader(text));
        //TODO: handle multiple sentences via this loop
        for (List<HasWord> tokenized: tokenizer) {
            System.out.println("tokenizer words " + tokenized);

            ParserQuery parserQuery = parser.parserQuery();
            parserQuery.parse(tokenized);
            //Need to determine how to flatten these alternate parse trees before returning them
            System.out.println("best parse " + parserQuery.getBestParse());
            System.out.println("alternative parses " + parserQuery.getBestPCFGParses());
            System.out.println("best PCFG parse " + parserQuery.getBestPCFGParse());

            List<TaggedWord> tagged = tagger.tagSentence(tokenized);
            System.out.println("tagged words " + tagged);
            parseResult = parseTagged(tagged);
            parseResult.tags = getTagStrings(tagged);

            parseResults.add(parseResult);

            parseResult = parseTaggedWithDependencyParser(tagged);
            if (null != parseResult) {
                parseResults.add(parseResult);
            }

            System.out.println("overlayed tags " + Arrays.toString(posTags));
            List<TaggedWord> customTagged = overlayTags(tagged, posTags);
            if (null != customTagged) {
                String[] customTagStrings = getTagStrings(customTagged);

                parseResult = parseTagged(customTagged);
                parseResult.tags = customTagStrings;
                parseResult.strategy += ".posTags";
                parseResults.add(parseResult);

                parseResult = parseTaggedWithDependencyParser(customTagged);
                if (null != parseResult) {
                    parseResult.tags = customTagStrings;
                    parseResult.strategy += ".posTags";
                    parseResults.add(parseResult);
                }
            }
        }

        return parseResults.toArray(new ParseResult[parseResults.size()]);
    }

    private static String[] getTagStrings(List<TaggedWord> tagged) {
        ArrayList<String> result = new ArrayList<String>();
        for (TaggedWord taggedWord: tagged) {
            result.add(taggedWord.toString());
        }
        return result.toArray(new String[result.size()]);
    }

    private static ParseResult parseTagged(List<TaggedWord> tagged) {
        ParserQuery parserQuery = parser.parserQuery();
        parserQuery.parse(tagged);
        ParseResult parseResult = new ParseResult();
        parseResult.tree = parserQuery.getBestParse().toString();
        parseResult.strategy = "PCFG.maxent";
        System.out.println("tagged parse " + parseResult.tree);
        return parseResult;
    }

    private static List<TaggedWord> overlayTags(List<TaggedWord> tagged, String[] posTags) {
        //Overlay pre-processed part of speech tags
        boolean appliedTag = false;
        for (int i = 0; i < posTags.length; i++) {
            String posTag = posTags[i];
            if (!"".equals(posTag)) {
                appliedTag = true;
                TaggedWord taggedWord = tagged.get(i);
                TaggedWord newTagged = new TaggedWord(taggedWord.value(), posTag);
                tagged.set(i, newTagged);
            }
        }
        if (!appliedTag) {
            return null;
        }
        return tagged;
    }

    private static ParseResult parseTaggedWithDependencyParser(List<TaggedWord> tagged) {
        if (null == dependencyParser) {
            return null;
        }
        ParseResult parseResult = new ParseResult();
        GrammaticalStructure grammaticalStructure = dependencyParser.predict(tagged);
        List<TypedDependency> typedDependencies = grammaticalStructure.typedDependenciesCCprocessed();
        String dependenciesString = typedDependencies.toString();
        parseResult.tree = null;
        parseResult.dependencies = dependenciesString;
        parseResult.strategy = "NNDEP";
        System.out.println("dependency parse " + parseResult.dependencies);
        return parseResult;
    }


}

class ParseRequest {
    String text = "";
    String[] posTags = {};

    ParseRequest() { }
}

class ParseResult {
    String tree = "";
    String dependencies = "";
    String[] tags = {};
    String strategy = "";

    ParseResult() { }
}

class ParseResponse {
    String tree = "";
    String dependencies = "";
    String strategy = "";
    String[] tags = {};
    ParseResult[] alternates = {};

    ParseResponse() { }
}
