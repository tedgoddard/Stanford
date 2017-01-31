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

public class Stanford {

    static String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
    static LexicalizedParser parser = LexicalizedParser.loadModel(parserModel);
    static String taggerPath = "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger";
    static MaxentTagger tagger = new MaxentTagger(taggerPath);

    public static void main(String[] args) {

        Spark.port(Integer.valueOf(System.getenv("STANFORD_PORT")));
        Spark.staticFileLocation("/public");

        Spark.get("/tree/:text", (request, response) -> {
            Gson gson = new Gson();
            String sentence = request.params(":text");
            ParseResult parseResult = parseText(sentence);

            String json = gson.toJson(parseResult);
            System.out.println("parse result " + json);

            return (json);
        });

        Spark.post("/tree/", (request, response) -> {
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

            ParseResult[] simpleAlternates = new ParseResult[] { parseResult, lowerResult };
            ParseResult[] otherAlternates = parseTextAlternates(sentence, posTags);
            parseResponse.alternates = Stream.concat(
                    Arrays.stream(simpleAlternates),
                    Arrays.stream(otherAlternates)).toArray(ParseResult[]::new);
            String json = gson.toJson(parseResponse);
            System.out.println(json);

            return (json);
        });

    }

    public static ParseResult parseText(String text) {
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

    public static ParseResult[] parseTextAlternates(String text, String[] posTags) {
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
            parseResults.add(parseResult);
            
            parseResult = parseTaggedWithTags(tagged, posTags);
            if (null != parseResult) {
                parseResults.add(parseResult);
            }
        }

        return parseResults.toArray(new ParseResult[parseResults.size()]);
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

    private static ParseResult parseTaggedWithTags(List<TaggedWord> tagged, String[] posTags) {
        ParserQuery parserQuery = parser.parserQuery();
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
        parserQuery.parse(tagged);
        ParseResult parseResult = new ParseResult();
        parseResult.tree = parserQuery.getBestParse().toString();
        parseResult.strategy = "PCFG.maxent.posTags";
        System.out.println("pre-tagged parse " + Arrays.toString(posTags) + " " + parseResult.tree);
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
    String strategy = "";

    ParseResult() { }
}

class ParseResponse {
    String tree = "";
    String dependencies = "";
    ParseResult[] alternates = {};

    ParseResponse() { }
}
