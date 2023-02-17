# GraphDB Example plugins

This is a sample project which aims to illustrate the use of the GraphDB Plugin API. Is is created entirely for training
purposes.

Additional documentation on the Plugin API and the project itself you can find
[here](http://graphdb.ontotext.com/free/plug-in-api.html) 

## Functionality

The project contains three plugins -- a basic plugin, a more complex one, and another slightly complex plugin.

### ExampleBasic plugin

The responsibility of the basic plugin is:

- It interprets the pattern `?s <http://example.com/now> ?o` and binds the object to a literal containing
the system date/time of the machine running GraphDB. The subject position is not used and its value does not matter.

- It interprets the pattern `?s <http://example.com/list> ?o` and binds the subject and object to a set of values:

    | ?s                      | ?o  |
    |-----|-----|
    | http://example.com/iri1 | "a" |
    | http://example.com/iri1 | "b" |
    | http://example.com/iri2 | "a" |
    | http://example.com/iri2 | "c" |

    This pattern will also take into account the values of ?s and ?o, if they are bound by other patterns in the same query.

### Example plugin

The complex plugin has more responsibilities:

- If a `FROM <http://example.com/time>` clause is detected in the query, the result is a single binding set in which
all projected variables are bound to a literal containing the system date/time of the machine running GraphDB.
- If a triple with the subject `http://example.com/time` and one of the predicates `http://example.com/goInFuture`
or `http://example.com/goInPast` is inserted, its object is set as a positive or negative offset for all future requests
querying the system date/time via the plugin.

### ExampleFunctional plugin

This plugin defines the predicate `http://example.com/getLabel` as a multiple-argument functional interface, where the function's arguments are provided as an RDF list in the object and the function output will be bound in the subject. 

It takes at least three arguments:

```
?label <http://example.com/getLabel> (?resource ?labelPredicate ?lang1 ...)
```

Where `?resource` is the RDF resource to lookup, `?labelPredicate` is the predicate whose object will be used, and `?lang1` and the remaining arguments are language tags provided as literals.

The plugin will return all labels that match the first language tag that has at least one label, or if none of the language tags match, all labels that are plain literals (i.e. `xsd:string` literals).

The language matching logic is compatible with the SPARQL `langMatches()` function.

It is trivial to add more functional patterns by implementing the `FunctionalPattern` interface and passing the instance to `#registerFunctionalPatterns(PluginConnection, FunctionalPattern...)`.


## Overview

The main plugin classes in this project are `ExamplePlugin` and `ExampleBasicPlugin`. They both extend
`com.ontotext.trree.sdk.PluginBase` -- the base class for all Plugins.

The interfaces which the plugins implement are:

- `PatternInterpreter` -- allows interpretation of basic triple patterns
- `UpdateInterpreter` -- allows interpretation of update triple patterns
- `Preprocessor` -- used to add context to a request at the beginning if the processing
- `Postprocessor` -- used to modify the query results

As the `PluginBase` implements `com.ontotext.trree.sdk.Service` we need to have a service descriptor in
`META-INT/services/`. 

## Deployment

Below you can find the deployment steps for this plugin:

1. In the `pom.xml` set the version of GraphDB you are using (9.0.0 or newer required).
This GraphDB version will be used for compiling and testing only.
2. In the service descriptor include the class of the plugin you want to build (by default both are included)
3. Build the project using `mvn clean package`
4. Unzip `./target/example-plugin-graphdb-plugin.zip` in  `<GDB_INST_DIR>/lib/plugins/`.

Once you start GraphDB and a repository is initialized you will see the following entries in the log:
```
Registering plugin exampleBasic
Initializing plugin 'exampleBasic'
ExampleBasic plugin initialized!
...
Registering plugin example
Initializing plugin 'example'
Example plugin initialized!
...
Registering plugin exampleFunctional
Initializing plugin 'exampleFunctional'
ExampleFunctional plugin initialized!
```

## Usage of the ExampleBasic plugin

### Get the time

Run the following query to retrieve the current system date/time:

```
SELECT ?o
WHERE {
    [] <http://example.com/now> ?o .
} 
```

The variable `?o` will be bound to a literal with the `xsd:dateTime` type.

### List and filter values

Insert the following simple data to provide a small set of humans that each have associated items:

```
INSERT DATA {
    <http://example.com/John> a <http://example.com/Human> ;
        <http://example.com/hasItem> <http://example.com/iri1> .
    <http://example.com/Mary> a <http://example.com/Human> ;
        <http://example.com/hasItem> <http://example.com/iri2> .
}
```

Then run this query to fetch the items associated with each human as well as the item parts (provided by the ExampleBasic plugin via the <http://example.com/list> predicate):

```
SELECT ?human ?item ?itemPart {
    ?human a <http://example.com/Human> ;
        <http://example.com/hasItem> ?item .
    ?item <http://example.com/list> ?itemPart
}
```

The results look like this:

| ?human                  | ?item                   | ?itemPart |
|-------------------------|-------------------------|-----------|
| http://example.com/John | http://example.com/iri1 | "a"       |
| http://example.com/John | http://example.com/iri1 | "b"       |
| http://example.com/Mary | http://example.com/iri2 | "a"       |
| http://example.com/Mary | http://example.com/iri2 | "c"       |


## Usage of the Example plugin

- Run the following query to get all unbound variables (in this case ?s, ?p and ?o) set to the system data/time plus optional offset:
```
SELECT *
FROM <http://example.com/time>
WHERE
{
    ?s ?p ?o .
}
```

The variables `?s`, `?p` and `?o` will be bound to literals with the `xsd:dateTime` type. 

- Set a system time offset of X hours in the future using this update:

```
# Adds two hours
INSERT DATA
{
    <http://example.com/time> <http://example.com/goInFuture> 2 .
}
```

- Set a system time offset of X hours in the past using this update:

```
# Removes one hour
INSERT DATA
{
    <http://example.com/time> <http://example.com/goInPast> 1 .
}
```

Once you run one of the inserts you can use the above select query to verify the result.

## Usage of the ExampleFunctional plugin

First, import the following Turtle data (also available in [src/test/resources/example-functional-data.ttl](src/test/resources/example-functional-data.ttl)).
It contains a couple of Star Trek movies with labels in different languages:

```
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix data: <http://example.com/data/> .
@prefix model: <http://example.com/model/> .

model:Setting1 model:labelPredicate rdfs:label ;
    model:language "es" .

data:StarTrekTOS a model:Movie ;
    rdfs:label "Star Trek: The Original Series"@en ,
        "La conquista del espacio"@es-ES ,
        "Viaje a las estrellas: la serie original"@es-LA .

data:StarTrekTNG a model:Movie ;
    rdfs:label "Star Trek: The Next Generation"@en ,
        "Star Trek: A Geração Seguinte"@pt-PT ,
        "Jornada nas Estrelas: A Nova Geração"@pt-BR ,
        "Star Trek: The Next Generation" .

data:StarTrekSNW a model:Movie ;
    rdfs:label "Star Trek: Fremde neue Welten"@de ,
        "Star Trek: Strange New Worlds" .
```

Then try running the following query:

```
PREFIX model: <http://example.com/model/>
SELECT ?movie ?label {
  ?movie a model:Movie .
  # Get the labels for a movie identified by ?movie, where the label is provided
  # by rdfs:label and the preferred language is German ("de")
  ?label <http://example.com/getLabel> (?movie rdfs:label "de")
}
```

The query will return two movies with one label each:

| ?movie             | ?label                             | Note                                        |
|--------------------|------------------------------------|---------------------------------------------|
| `data:StarTrekTNG` | "Star Trek: The Next Generation"   | No German label but has a plain literal one |
| `data:StarTrekSNW` | "Star Trek: Fremde neue Welten"@de | Has a German label                          |

You can also specify more than one preferred language and they will be tried in order of preference:

```
PREFIX model: <http://example.com/model/>
SELECT ?movie ?label {
  ?movie a model:Movie .
  # Get the labels for a movie identified by ?movie, where the label is provided
  # by rdfs:label and the preferred languages are European Portuguese and English in that order
  ?label <http://example.com/getLabel> (?movie rdfs:label "pt-PT" "en")
}
```

The query will return three movies with one label each:

| ?movie             | ?label                                | Note                                                                      |
|--------------------|---------------------------------------|---------------------------------------------------------------------------|
| `data:StarTrekTOS` | "Star Trek: The Original Series"@en   | No Portuguese (Portugal) label but has an English one                     |
| `data:StarTrekTNG` | "Star Trek: A Geração Seguinte"@pt-PT | Has a Portuguese (Portugal) label                                         |
| `data:StarTrekSNW` | "Star Trek: Strange New Worlds"       | No Portuguese (Portugal) and no English label but has a plain literal one |


Now, let's try fetching the predicate of the label and the preferred language from the database,
where the retrieved label predicate is `rdfs:label` and the retrieved language is "es":

```
PREFIX model: <http://example.com/model/>
SELECT ?movie ?label {
  # Fetch label predicata and language from data stored under model:Setting1
  model:Setting1 model:labelPredicate ?labelPred ;
    model:language ?language .
  ?movie a model:Movie .
  # Get the labels for a movie identified by ?movie, where the label is provided
  # by the variable ?labelPred and the preferred language by the variable ?language
  ?label <http://example.com/getLabel> (?movie ?labelPred ?language)
}
```

We'll get three movies, one of which has two labels in the preferred language -- once the first preferred language matches at least one label
it will return all labels matching that language:

| ?movie             | ?label                                           | Note                                                                            |
|--------------------|--------------------------------------------------|---------------------------------------------------------------------------------|
| `data:StarTrekTOS` | "La conquista del espacio"@es-ES                 | Has a Spanish (Spain) label that matches the requested language Spanish         |
| `data:StarTrekTOS` | "Viaje a las estrellas: la serie original"@es-LA | Has a Spanish (Latin America) label that matches the requested language Spanish |
| `data:StarTrekTNG` | "Star Trek: The Next Generation"                 | No Spanish label but it has a plain literal one                                 |
| `data:StarTrekSNW` | "Star Trek: Strange New Worlds"                  | No Spanish label but it has a plain literal one                                 |


## Caution

Please be extremely careful when adding a new plugin to GraphDB. Faulty plugins can have a devastating effect on the
system -- they can affect the performance negatively, cause memory leaks or lead to non-deterministic behaviour!
