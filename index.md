# StringAnnotation Plugin

A plugin for the GATE language technology framework that provides gazetteer and regular expression annotator PRs for string annotation.

The GATE String Annotation Plugin provides processing resources for annotating document text based on gazetteer lists (similar to the GATE Default Gazetteer) or Java regular expressions. The processing resources also provide built-in support for annotating based on the values of annotation features instead of the original document text (similar to what can be achieved with the FlexibleGazetteer). In addition, there is a processing resource for annotating, updating or deleting annotations if some feature value matches an entry in a gazetteer list.


* Get the [source code on GitHub](https://github.com/GateNLP/gateplugin-StringAnnotation)
* Submit a [bug report or enhancement request]
* JavaDoc 
* Pull requests are welcome, but if you want to contribute, it may be better to submit an issue and/or to get in touch first to coordinate plans


## Documentation Overview

The plugin has the following processing resources (PRs):
* [ExtendedGazetteer](ExtendedGazetteer): a processing resource to annotate documents based on gazetteer lists if the document text or the feature values of some annotatation type match. Much more scalable and flexible than the gazetteer implementations included with the GATE distribution
* [FeatureGazetteer](FeatureGazetteer): a processing resource to annotated, update or delete annotations if a feature matches entries in a gazetteer list
* [JavaRegexpAnnotator](JavaRegexpAnnotator): a processing resource to annotate documents based on Java regular expressions if the document text or the feature values of some annotation type match.


