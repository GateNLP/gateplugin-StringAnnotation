# Processing Resource FeatureGazetteer

## Overview

The purpose of this gazetteer is to match gazetteer entries against the values of a feature of some annotations and then perform one of several possible actions (parameter `processingMode`) if a match is found:
* add features to the existing features of the annotation from the lookup
* add or overwrite featurs of the existing annotation from the lookup
* remove the annotation
* add a new annotation 

Other than with the indirect annotation mode of the ExtendedGazetteer PR, where all annotations together define a large text that is used for matching, the matching is done on an annotation-by-annotation basis: a match is attempted for the feature value of each annotation seperately.

This is useful for e.g. the following tasks:
* enrich annotations with information from a "lookup-gazetteer". For example, if tokens have a feature "root" and there is a gazetteer list that has as a feature the frequencies of english word roots in some corpus, the "add features" action can be used to enrich the token anntoations with word frequencies.
* filter annotations: For example, if there is a gazetteer of stopwords, the string or root feature of existing word/token annotations can be matched and the "remove annotation" action can be used to remove these annotations if a stopword is matched. 

## Init Time Parameters ##

* `configFileURL` (URL, no default): the URL of the configuration file (this must be a file: URL). This can either be a file with the extension `.def` which is similar to the format used by the GATE Default Gazetteer, or the new, more expressive YAML config file format (see [Configuration and List Files](#configfile) below).
* `caseSensitive` (boolean, default=true): if the gazetteer list entries should be stored in a case-normalized way and if the text should get case-normalized too before matching. This has to be an init time parameter because the internal data created will be different if `caseSensitive` is `false`. If `caseSensitive` is set to `false` all entries are converted to upper case. This will be done in a character-by-character fashion but the PR will also convert the whole entry, using the language specified as `caseConversionLanguage`. Thus, one-to-many case coversions (like German "ß"->"SS") and language specific conversions (like Turkish "i"->"İ") should work.
* `caseConversionLanguage` (String, default=en): The language to use when converting an entry to all-upper-case for case normalization, if case sensitive matching is turned off. 

NOTE: the parameter `gazetteerFeatureSeparator` which has been available in previous versions of this plugin has been removed. All gazetteer list files now *must* use the tab character for field separation.

## Runtime Parameters ##

* `containingAnnotationType` (String, no default): If an annotation type is given, then matching is done only within the span of such annotations. Matches will never span across the beginning or end of an containing annotation. Containing annotations should never overlap - if they do, results are undefined and this may cause a program exception.
* `matchAtStartOnly` (boolean, default=true): if true, then a match must be found at the start of the value of the feature, if false, a match may start anywhere.
* `matchAtEndOnly` (boolean, default=true): if true, then a match must be found that ends at the end of the value of the feature, if false, amatch may end anywhere.
* `inputAnnotationSet` (String, default=default annotation set): if specified, use the annotation set with that name instead of the default annotation set.
* `outputAnnotationSet` (string, default=default annotation set): if specified, use the annotation set with that name isntead of the default annotation set for new annotations. Ignored if a `processingMode` is used that does not create annotations.
* `outputAnnotationType` (string, no default): if this is set, it will be used as the annotation type for all created annotations. If it is not set, then the annotation type defined in the config file for this list is used, and if that is also empty, "Lookup" is used.
* `processingMode` (enumeration, default=AddFeatures): can be one of the following:
  * `AddFeatures`: all features from the def file or the gazetteer entry which are not already present in the annotation are added
  * `OverwriteFeatures`: all features from the def file or the gazetteer entry are set in the annotation, if they were already present, they are overwritten with the new values
  * `RemoveAnnotation`: the annotation is deleted from the input annotation set
  * `AddNewAnnotation`: a new annotation is addded to the output annotation set 
* `textFeature` (String, default: string, required): the name of a feature of the word annotation which is used for matching. If at least one gazetteer entry can be matched against the value of this feature (depending on the fullMatchesOnly parameter), then the chosen processingMode action is performed.
* `wordAnnotationType` (String, default=Token): the annotation type that is used for matching. 

