# StanfordService

A simple web service providing CoreNLP parsing.

## Build

```
curl http://nlp.stanford.edu/software/stanford-english-corenlp-2016-10-31-models.jar -o lib/stanford-english-corenlp-2016-10-31-models.jar
javac -cp ".:lib/*" Stanford.java
```

## RUN

```
java -cp ".:lib/*" Stanford
```

## USE

```
curl "http://localhost:8888/tree/Does%2FVBZ%20my%20dog%20like%20to%20eat%20sausages%3F"
```
