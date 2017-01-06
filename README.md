# StanfordService

## Build

```
curl http://nlp.stanford.edu/software/stanford-english-corenlp-2016-10-31-models.jar -o lib/stanford-english-corenlp-2016-10-31-models.jar
javac -cp ".:lib/*" Stanford.java
```

## RUN

```
java -cp ".:lib/*" Stanford
```
