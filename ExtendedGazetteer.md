# Processing Resource ExtendedGazetteer

This processing resource works similar to the DefaultGazetteer included in the GATE distribution: it will read one or more gazetteer list files which may or may not contain feature values and use those lists to identify and annotate text spans in a document which match one or more of the entries in the lists. However, this implementation is also different in several important ways from the DefaultGazetteer:
* it scales much better to very large gazetteer lists: with the same amount of availabel memory, it can read in lists with many more gazetteer entries
* it caches the binary representation of the loaded gazetteer lists: after successfully reading in all the gazetteer lists this PR will save a binary cache (a file with the file extension ".gazbin") and will only load that cache when it is found from then on. Loading data from the cache file is much faster and requires less memory. See [Caching](#caching) below.
* it can be used to annotate either the original document text (similar to the Default Gazetteer) or the "virtual document" created from the values of a specified annotation type (similar to how wrapping the Default Gazetteer inside the Flexible Gazetteer works). 
* it requires "word annotations" (e.g. Token annotations) and "non-word/space" annotations (e.g. SpaceToken annotations) even when the original document text should get annotated. This is because the Extended Gazetteer uses the word/non-word annotations to determine where the word boundaries are while the Default Gazetteer has its own heuristics (which may not match whatever tokenisation strategy is used). In addition the Extended Gazetteer considers split annotations (gazetteer matches will never match a split annotation, even when indirect annotation is performed, thus e.g. preventing matches from crossing over sentence boundaries) and containing annotations (matches will only be found within any of the containing annotations in the document). The word annotations are also used to make "direct/indirect" annotation easier to choose: if not textFeature is specified, the underlying document is used, otherwise the value of that feature. Thus, the functionality of the Flexible Gazetteer is directly integrated in this PR.
* All files (config file, list files) have to use UTF-8 encoding, it is not possible to specify an alternate encoding.

## Init Time Parameters ##

* `configFileURL` (URL, no default): the URL of the configuration file (this must be a file: URL). This can either be a file with the extension `.def` which is similar to the format used by the GATE Default Gazetteer, or the new, more expressive YAML config file format (see [Configuration and List Files](#configfile) below).
* `caseSensitive` (boolean, default=true): if the gazetteer list entries should be stored in a case-normalized way and if the text should get case-normalized too before matching. This has to be an init time parameter because the internal data created will be different if `caseSensitive` is `false`. If `caseSensitive` is set to `false` all entries are converted to upper case. This will be done in a character-by-character fashion but the PR will also convert the whole entry, using the language specified as `caseConversionLanguage`. Thus, one-to-many case coversions (like German "ß"->"SS") and language specific conversions (like Turkish "i"->"İ") should work.
* `caseConversionLanguage` (String, default=en): The language to use when converting an entry to all-upper-case for case normalization, if case sensitive matching is turned off. 

NOTE: the parameter `gazetteerFeatureSeparator` which has been available in previous versions of this plugin has been removed. All gazetteer list files now *must* use the tab character for field separation.

## Runtime Parameters ##

* `containingAnnotationType` (String, no default):  If an annotation type is given, then matching is done only within the span of such annotations. Matches will never span across the beginning or end of an containing annotation. Containing annotations should never overlap - if they do, results are undefined and this may cause a program exception. 
* `inputAnnotationSet` (String, default="" for the default annotation set): if specified, use the annotation set with that name instead of the default annotation set. 
* `longestMatchOnly` (boolean, default=true): if several matches are possible at one location in the text and this is set to true, then only the longest ones are used and all matches shorter than the longest ones are ignored. 
* `matchAtWordEndOnly` (boolean, default=true): if this is true, then the end of a match can only occur at the end of a word annotation (see parameter `wordAnnotationType`) 
* `matchAtWordStartOnly` (boolean, default=true): if this is true, then the start of a match can only occur at the start of a word annotation (see parameter `wordAnnotationType`)
* `outputAnnotationSet` (String, default="" for the default annotation set): if specified, use the annotation set with that name instead of the default annotation set for new annotations. 
* `outputAnnotationType` (String, no default): if this is set, it will be used as the annotation type for all created annotations. If it is not set, then the annotation type defined in the config file for this list is used, and if that is also empty, "Lookup" is used. 
* `spaceAnnotationType` (String, default=SpaceToken): the annotation type that identifies space between words. One or more such tokens will be matched against space within gazetteer entries. 
* `splitAnnotationType` (String, default=Split): the annotation type that identifies positions in the document that should not be crossed by matches. If this is set, no match will ever cross any occurrence of such an annotation. If not specified/empty, no split annotations are considered. 
* `textFeature` (String, default: none): if specified, the name of a feature of the word annotation which is used instead of the underlying document text. This provides essentially the functionality of the FlexibleGazetteer or the IndirectExtendedGazetteer. Note that if matches are not restricted to word start or word end, and this is used to match against the text from a feature, then if a match occurs anywhere in the text of some word annotation, the annotation that is created will always start at the start of that word annotation (and analogously for the end). If this feature does not exist or is empty for an annotation, the whole annotation is ignored.
* `wordAnnotationType` (String, default=Token): the annotation type that identifies the text that should be used for matching. If the textFeature parameter is empty, then the underlying document text of these anntoations is used, otherwise the value of the that feature. 

## <a name="configfile"></a>Configuration and List Files ##

## <a name="caching"></a>Caching ##

When a gazetteer is first loaded for a config file (a .def or .yaml file, which in turns describes the gazetteer list files to load), then the ExtendedGazetteer PR will create a new gazetteer cache file. This cache file has the same name as the config file but with the file extension replaced by ".gazbin". When the gazetteer gets loaded and such a cache file exists, the cache file will be loaded instead of the original files.

**NOTE: if a cache file exists, it will always be used, no matter if the config or gazetteer list files have been changed in the meantime!!!** This behavior has been implemented to make it easier for users to explicitly choose when to update the chache file. There is no automatism (e.g. modification dates, checksums etc). Instead, if the cache file should get re-created, simply delete it. 

## Multithreading, Custom Duplication ##

If a gazetteer has been loaded from some config file using some particular case-sensitivity setting, then any new instance of a gazetteer PR (either ExtendedGazetteer or FeatureGazetteer) that use the same files and case sensitivity setting will automatically share the loaded data. This avoids using up memory for something that can be shared between several PRs. This is especially useful in situations where the same pipeline (with identical gazetteers) is loaded several times in order to process documents in parallel. 

The ExtendedGazetteer and FeatureGazetteer PRs both will share their data if they are duplicated by the GATE Factory. 

## Using from the GUI

* The gazetteer does not support editing the gazetteer files from within the GUI. This feature would require the whole of all list files to get loaded into memory and since one of the design goals of this implementation was to be able to support very large gazetteers, the list files do not get loaded into memory.
* An additional entry is added to the action menu for this PR: "Remove cache and re-initialize". This action will remove the cache file ".gazbin" before re-loading the gazetteer from the list files and thus creating a new, updated version of the cache file.
* If two PRs share the same GazStore and one of them gets re-initialized with a changed version, the other PRs will not see that change until they get re-initialized too
