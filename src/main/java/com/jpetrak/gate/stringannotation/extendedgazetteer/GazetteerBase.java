/*
 * Copyright (c) 2010- Austrian Research Institute for Artificial Intelligence (OFAI). 
 * Copyright (C) 2014-2019 The University of Sheffield.
 *
 * This file is part of gateplugin-ModularPipelines
 * (see https://github.com/johann-petrak/gateplugin-StringAnnotation)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jpetrak.gate.stringannotation.extendedgazetteer;

import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.apache.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import com.jpetrak.gate.stringannotation.extendedgazetteer.trie.GazStoreTrie3;
import com.jpetrak.gate.stringannotation.utils.UrlUtils;

import gate.Factory;
import gate.FeatureMap;
import gate.GateConstants;
import gate.Resource;
import gate.creole.ANNIEConstants;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ResourceInstantiationException;
import gate.creole.ResourceReference;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.Optional;
import gate.gui.ActionsPublisher;
import gate.util.BomStrippingInputStreamReader;
import gate.util.Files;
import gate.util.GateRuntimeException;
import gate.util.Strings;

/**
 * Common Base class for all gazetteer implementations. All these PRs need to
 * have the config file URL, case sensitivity init parameters so the parameters
 * and the loading logic is moved into this base class.
 *
 * @author Johann Petrak
 */
public abstract class GazetteerBase extends AbstractLanguageAnalyser implements ActionsPublisher {

  private static final long serialVersionUID = -6812186693426581898L;

  @CreoleParameter(comment = "The URL to the gazetteer configuration file", suffixes = "def;defyaml", defaultValue = "")
  public void setConfigFileURL(ResourceReference theURL) {
    configFileURL = theURL;
  }

  public ResourceReference getConfigFileURL() {
    return configFileURL;
  }
  protected ResourceReference configFileURL;

  @CreoleParameter(comment = "Should this gazetteer differentiate on case",
          defaultValue = "true")
  public void setCaseSensitive(Boolean yesno) {
    // System.err.println("DEBUG: setting case sensitive to " + yesno);
    caseSensitive = yesno;
  }

  public Boolean getCaseSensitive() {
    return caseSensitive;
  }
  protected Boolean caseSensitive;

  @CreoleParameter(comment = "For case insensitive matches, the locale to use for normalizing case",
          defaultValue = "en")
  public void setCaseConversionLanguage(String val) {
    // System.err.println("DEBUG: case conversion language set to " + val);
    caseConversionLanguage = val;
    caseConversionLocale = new Locale(val);
  }

  public String getCaseConversionLanguage() {
    return caseConversionLanguage;
  }
  protected String caseConversionLanguage;

  @CreoleParameter(
          comment = "The character used to separate features for entries in gazetteer lists. Accepts strings like &quot;\t&quot; and will unescape it to the relevant character. If not specified, a tab character will be used",
          defaultValue = "\\t"
  )
  @Optional
  public void setGazetteerFeatureSeparator(String sep) {
    gazetteerFeatureSeparator = sep;
    if (sep == null || sep.isEmpty()) {
      unescapedSeparator = Strings.unescape("\\t");
    } else {
      unescapedSeparator = Strings.unescape(sep);
    }
  }

  public String getGazetteerFeatureSeparator() {
    return gazetteerFeatureSeparator;
  }

  protected String gazetteerFeatureSeparator = "\\t";
  protected String unescapedSeparator = Strings.unescape("\\t");
  protected Locale caseConversionLocale = Locale.ENGLISH;
  protected Logger logger;
  //protected CharMapState initialState;
  protected GazStore gazStore;
  private static final int MAX_FEATURES_PER_ENTRY = 500;
  protected static Pattern ws_pattern;
  protected static final String WS_CHARS
          = "\\u0009" // CHARACTER TABULATION
          + "\\u000A" // LINE FEED (LF)
          + "\\u000B" // LINE TABULATION
          + "\\u000C" // FORM FEED (FF)
          + "\\u000D" // CARRIAGE RETURN (CR)
          + "\\u0020" // SPACE
          + "\\u0085" // NEXT LINE (NEL) 
          + "\\u00A0" // NO-BREAK SPACE
          + "\\u1680" // OGHAM SPACE MARK
          + "\\u180E" // MONGOLIAN VOWEL SEPARATOR
          + "\\u2000" // EN QUAD 
          + "\\u2001" // EM QUAD 
          + "\\u2002" // EN SPACE
          + "\\u2003" // EM SPACE
          + "\\u2004" // THREE-PER-EM SPACE
          + "\\u2005" // FOUR-PER-EM SPACE
          + "\\u2006" // SIX-PER-EM SPACE
          + "\\u2007" // FIGURE SPACE
          + "\\u2008" // PUNCTUATION SPACE
          + "\\u2009" // THIN SPACE
          + "\\u200A" // HAIR SPACE
          + "\\u2028" // LINE SEPARATOR
          + "\\u2029" // PARAGRAPH SEPARATOR
          + "\\u202F" // NARROW NO-BREAK SPACE
          + "\\u205F" // MEDIUM MATHEMATICAL SPACE
          + "\\u3000" // IDEOGRAPHIC SPACE
          ;
  protected static final String WS_CLASS = "[" + WS_CHARS + "]";
  protected static final String WS_PATTERNSTRING = WS_CLASS + "+";
  protected static final String UTF8 = "UTF-8";

  public GazetteerBase() {
    logger = Logger.getLogger(this.getClass().getName());
  }

  @Override
  public Resource init() throws ResourceInstantiationException {
    // precompile the pattern used to replace all unicode whitespace in gazetteer
    // entries with a single space.
    ws_pattern = Pattern.compile(WS_PATTERNSTRING);
    // System.err.println("DEBUG: running init(), caseConversionLanguage is " + caseConversionLanguage);
    incrementGazStore();
    return this;
  }
  final protected static Map<String, GazStore> loadedGazStores = new HashMap<String, GazStore>();

  public synchronized void incrementGazStore() throws ResourceInstantiationException {
    // System.err.println("DEBUG running incrementGazStore");
    String uniqueGazStoreKey = genUniqueGazStoreKey();
    logger.info("Creating gazetteer for " + getConfigFileURL());

    boolean profile = (System.getProperty("com.jpetrak.gate.stringannotation.profile") != null);

    long startTime = 0, before = 0;

    if (profile) {
       System.gc();
       startTime = System.currentTimeMillis();
       before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    GazStore gs = loadedGazStores.get(uniqueGazStoreKey);
    if (gs != null) {
      // The FSM for this file/parm combination already has been compiled, just
      // reuse it for this PR
      gazStore = gs;
      gazStore.refcount++;
      logger.info("Reusing already generated GazStore for " + uniqueGazStoreKey);
    } else {
      try {
        loadData();
        gazStore.compact();
      } catch (ResourceInstantiationException | IOException ex) {
        throw new ResourceInstantiationException("Could not load gazetteer", ex);
      }

      gazStore.refcount++;
      loadedGazStores.put(uniqueGazStoreKey, gazStore);
      // System.err.println("DEBUG addeed new gaz store with key " + uniqueGazStoreKey);
      logger.info("New GazStore loaded for " + uniqueGazStoreKey);
    }

    if (profile) {
       long endTime = System.currentTimeMillis();
       System.gc();
       long after = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
       logger.info("Gazetteer created in (secs):          " + ((endTime - startTime) / 1000.0));
       logger.info("Heap memory increase (estimate,MB):   "
               + String.format("%01.3f", ((after - before) / (1024.0 * 1024.0))));
       logger.info(gazStore.statsString());
    }
  }

  public synchronized void replaceGazStore() throws ResourceInstantiationException {
    String uniqueGazStoreKey = genUniqueGazStoreKey();
    logger.info("Replacing gazetteer for " + getConfigFileURL());

    long startTime = 0, before = 0;

    boolean profile = (System.getProperty("com.jpetrak.gate.stringannotation.profile") != null);

    if (profile) {
       System.gc();
       startTime = System.currentTimeMillis();
       before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    try {
      loadData();
      gazStore.compact();
    } catch (ResourceInstantiationException | IOException ex) {
      throw new ResourceInstantiationException("Could not load gazetteer", ex);
    }
    loadedGazStores.put(uniqueGazStoreKey, gazStore);
    logger.info("GazStore replaced for " + uniqueGazStoreKey);

    if (profile) {
       long endTime = System.currentTimeMillis();
       System.gc();
       long after = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
       logger.info("Gazetteer created in (secs):          " + ((endTime - startTime) / 1000.0));
       logger.info("Heap memory increase (estimate,MB):   "
               + String.format("%01.3f", ((after - before) / (1024.0 * 1024.0))));
       logger.info(gazStore.statsString());
    }
  }

  public synchronized void decrementGazStore() {
    // System.err.println("DEBUG:  running decrementGazStore, map contains: " + loadedGazStores.keySet());
    String key = genUniqueGazStoreKey();
    // System.err.println("DEBUG: key for finding the gaz store: " + key);
    GazStore gs = loadedGazStores.get(key);
    // System.err.println("DEBUG got a gaz store: " + gs);
    gs.refcount--;
    if (gs.refcount == 0) {
      // System.err.println("DEBUG: removing gaz store key");
      loadedGazStores.remove(key);
      logger.info("Removing GazStore for " + key);
    }
  }

  public synchronized void removeGazStore() {
    // System.err.println("DEBUG: running removeGazStore()");
    String key = genUniqueGazStoreKey();
    // System.err.println("DEBUG: removing gazstore key: " + key);
    loadedGazStores.remove(key);
    logger.info("reInit(): force-removing GazStore for " + key);
  }

  protected String genUniqueGazStoreKey() {
    String key = " cs=" + caseSensitive + " url=" + configFileURL + " lang=" + caseConversionLanguage;
    // System.err.println("DEBUG: generating the gaz store key: " + key);
    return key;
  }

  @Override
  public void cleanup() {
    // System.err.println("DEBUG: running cleanup()");
    decrementGazStore();
  }

  public void save(File whereTo) throws IOException {
    gazStore.save(whereTo);
  }

  @Override
  /**
   */
  public void reInit() throws ResourceInstantiationException {
    //removeGazStore();
    //init();
    // System.err.println("DEBUG: running reInit()");
    replaceGazStore();
  }

  protected void loadData() throws UnsupportedEncodingException, IOException, ResourceInstantiationException {
    // if we find the cache file, load it, else load the original files and create the cache file

    //!File configFile = gate.util.Files.fileFromURL(configFileURL);
    // check the extension and determine if we have an old format .def file or 
    // a new format .defyaml file
    String tmp_name = UrlUtils.getName(configFileURL.toURL());
    int i = tmp_name.lastIndexOf('.') + 1;
    if (i < 0) {
      throw new GateRuntimeException("Config file must have a .def or .defyaml extension");
    }
    String ext = tmp_name.substring(i);
    if (ext.isEmpty() || !(ext.equals("def") || ext.equals("defyaml"))) {
      throw new GateRuntimeException("Config file must have a .def or .defyaml extension");
    }
    if (ext.equals("def")) {
      loadDataFromDef(configFileURL.toURL());
    } else {
      loadDataFromYaml(configFileURL.toURL());
    }
  }

  public static String makeCacheKey(boolean caseSensitive, String caseConversionLanguage) {
    String csIndicator = caseSensitive ? "c1" : "c0";
    String ccl = "en";
    if(caseConversionLanguage != null && !caseConversionLanguage.isEmpty()) {
      ccl = caseConversionLanguage;    
    }
    return csIndicator+"_"+ccl;
  }
    
  private String getCacheKey() {
    return makeCacheKey(caseSensitive, caseConversionLanguage);
  }
  
  
  
  protected void loadDataFromDef(URL configFileURL) throws IOException {
    String configFileName = configFileURL.toExternalForm();
    String gazbinFileName = configFileName.replaceAll("\\.def$", "_"+getCacheKey()+".gazbin");
    if (configFileName.equals(gazbinFileName)) {
      throw new GateRuntimeException("Config file must have def or defyaml extension, not " + configFileURL);
    }
    URL gazbinURL = new URL(gazbinFileName);
    gazStore = null;
    if (UrlUtils.exists(gazbinURL)) {
      // if something goes wrong loading the cache, this will show a message and return null
      try {
        gazStore = GazStoreTrie3.load(gazbinURL);
      } catch (GateRuntimeException ex) {
        ex.printStackTrace(System.err);
        System.err.println("WARNING: loading from original files, could not load gazbin file "+gazbinURL);        
      }
    } 
    if(gazStore == null) {
      gazStore = new GazStoreTrie3();
      try (BufferedReader defReader = new BomStrippingInputStreamReader((configFileURL).openStream(), UTF8)) {
        String line;
        //logger.info("Loading data");
        while (null != (line = defReader.readLine())) {
          String[] fields = line.split(":");
          if (fields.length == 0) {
            System.err.println("Empty line in file " + configFileURL);
          } else {
            String listFileName;
            String majorType = "";
            String minorType = "";
            String languages = "";
            String annotationType = ANNIEConstants.LOOKUP_ANNOTATION_TYPE;
            listFileName = fields[0];
            if (fields.length > 1) {
              majorType = fields[1];
            }
            if (fields.length > 2) {
              minorType = fields[2];
            }
            if (fields.length > 3) {
              languages = fields[3];
            }
            if (fields.length > 4) {
              annotationType = fields[4];
            }
            if (fields.length > 5) {
              throw new GateRuntimeException("Line has more that 5 fields in def file " + configFileURL);
            }
            logger.debug("Reading from " + listFileName + ", " + majorType + "/" + minorType + "/" + languages + "/" + annotationType);
            //logger.info("DEBUG: loading data from "+listFileName);
            loadListFile(listFileName, majorType, minorType, languages, annotationType);
          }
        } //while
      } // try
      gazStore.compact();
      logger.info("Gazetteer loaded from list files");

      // only write the cache if we loaded the def file from an actual file, not
      // some other URL
      if (UrlUtils.isFile(gazbinURL)) {
        File gazbinFile = Files.fileFromURL(gazbinURL);
        if (gazbinFile.exists()) {
          System.err.println("Warning: re-created cache but cache file already exists, please remove: " + gazbinURL);
        } else {
          try {
            gazStore.save(gazbinFile);
          } catch (IOException ex) {
            ex.printStackTrace(System.err);
            System.err.println("WARNING: error writing to " + gazbinURL + ", maybe not created or replaced or damaged!");
          }
        }
      }
    } // gazbinFile exists ... else
  }

  @SuppressWarnings("unchecked")
  protected void loadDataFromYaml(URL configFileURL) throws IOException {
    String configFileName = configFileURL.toExternalForm();
    String gazbinFileName = configFileName.replaceAll("\\.defyaml$", "_"+getCacheKey()+".gazbin");
    if (configFileName.equals(gazbinFileName)) {
      throw new GateRuntimeException("Config file must have def or defyaml extension");
    }
    URL gazbinURL = new URL(gazbinFileName);

    String gazbinDir = UrlUtils.getParent(gazbinURL);
    String gazbinName = UrlUtils.getName(gazbinURL);

    // Always read the yaml file so we can get any special location of the cache
    // file or figure out that we should not try to load the cache file
    Yaml yaml = new Yaml();
    BufferedReader yamlReader
            = new BomStrippingInputStreamReader((configFileURL).openStream(), UTF8);
    Object configObject = yaml.load(yamlReader);

    List<Map<String, Object>> configListFiles = null;
    if (configObject instanceof Map) {
      Map<String, Object> configMap = (Map<String, Object>) configObject;
      String configCacheDirName = (String) configMap.get("cacheDir");
      if (configCacheDirName != null) {
        gazbinDir = configCacheDirName;
      }
      String configCacheFileName = (String) configMap.get("chacheFile");
      if (configCacheFileName != null) {
        gazbinName = configCacheFileName;
      }
      gazbinURL = UrlUtils.newURL(new URL(gazbinDir), gazbinName);
      configListFiles = (List<Map<String, Object>>) configMap.get("listFiles");
    } else if (configObject instanceof List) {
      configListFiles = (List<Map<String, Object>>) configObject;
    } else {
      throw new GateRuntimeException("Strange YAML format for the defyaml file " + configFileURL);
    }

    gazStore = null;
    // if we want to load the cache and it exists, load it
    if (UrlUtils.exists(gazbinURL) ) {
      try {
        gazStore = GazStoreTrie3.load(gazbinURL);
      } catch(GateRuntimeException ex) {
        ex.printStackTrace(System.err);
        System.err.println("WARNING: loading from original files, could not load gazbin file "+gazbinURL);
      }
    }
    if (gazStore == null) {
      gazStore = new GazStoreTrie3();
      // go through all the list and tsv files to load and load them
      for (Map<String, Object> configListFile : configListFiles) {
      } // TODO!!!
      //logger.debug("Reading from "+listFileName+", "+majorType+"/"+minorType+"/"+languages+"/"+annotationType);
      //logger.info("DEBUG: loading data from "+listFileName);
      //loadListFile(listFileName,majorType,minorType,languages,annotationType);
      //while
      gazStore.compact();
      logger.info("Gazetteer loaded from list files");

      if (UrlUtils.isFile(gazbinURL)) {
        File gazbinFile = Files.fileFromURL(gazbinURL);
        if(gazbinFile.canWrite()) {
          try {
            if(gazbinFile.exists()) {
              System.err.println("Warning: re-created cache but cache file already exists, please remove: "+gazbinURL);
            } else {            
              gazStore.save(gazbinFile);
            }
          } catch (IOException ex) {
            ex.printStackTrace(System.err);
            System.err.println("WARNING: error writing to "+gazbinURL+", not created or replaced!");            
          }
        } else {
          System.err.println("WARNING: cannot write to "+gazbinURL+", not created or replaced!");
        }
      }
    }
  }

  void loadListFile(String listFileName, String majorType, String minorType,
          String languages, String annotationType)
          throws MalformedURLException, IOException {

    //logger.info("Loading list file "+listFileName);
    URL lurl = new URL(configFileURL.toURL(), listFileName);
    FeatureMap listFeatures = Factory.newFeatureMap();
    listFeatures.put(LOOKUP_MAJOR_TYPE_FEATURE_NAME, majorType);
    listFeatures.put(LOOKUP_MINOR_TYPE_FEATURE_NAME, minorType);
    if (languages != null) {
      listFeatures.put(LOOKUP_LANGUAGE_FEATURE_NAME, languages);
    }
    int infoIndex = gazStore.addListInfo(annotationType, lurl.toString(), listFeatures);
    //Lookup defaultLookup = new Lookup(listFileName, majorType, minorType, 
    //        languages, annotationType);
    BufferedReader listReader;
    if (listFileName.endsWith(".gz")) {
      listReader = new BufferedReader(new InputStreamReader(new GZIPInputStream(lurl.openStream()), UTF8));
    } else {
      listReader = new BomStrippingInputStreamReader(lurl.openStream(), UTF8);
    }
    String line;
    int lines = 0;
    String[] entryFeatures;
    while (null != (line = listReader.readLine())) {
      entryFeatures = new String[0];
      lines++;
      String entry = line;
      // check if we have a separator in the line, if yes, we should take
      // the part before the first separator to be the entry and extract
      // the features from everything that comes after it.
      // All this only, if the separator is set at all
      if (unescapedSeparator != null) {
        int firstSepIndex = line.indexOf(unescapedSeparator);
        if (firstSepIndex > -1) {          
          entry = line.substring(0, firstSepIndex);          
          // split the rest of the line real fast
          int lastSepIndex = firstSepIndex;
          int nrFeatures = 0;
          String[] featureBuffer = new String[MAX_FEATURES_PER_ENTRY * 2];
          int nextSepIndex = 0;
          do {
            //logger.info("Feature nr: "+(nrFeatures+1));
            // check if we already have maximum number of features allows
            if (nrFeatures == MAX_FEATURES_PER_ENTRY) {
              throw new GateRuntimeException(
                      "More than " + MAX_FEATURES_PER_ENTRY + " features in gazetteer entry in list " + listFileName
                      + " line " + lines);
            }
            // get the index of the next separator
            nextSepIndex = line.indexOf(unescapedSeparator, lastSepIndex + 1);
            if (nextSepIndex < 0) { // if none found, use beyond end of String
              nextSepIndex = line.length();
            }
            // first of all, check if the field between the last and next seps is zero length, if yes
            // just ignore it (see issue #24
            if (nextSepIndex-lastSepIndex == 1) {
              lastSepIndex = nextSepIndex;
              continue;
            }
            // find the first equals character in the string section for this feature
            int equalsIndex = line.indexOf('=', lastSepIndex + 1);
            //logger.info("lastSepIndex="+lastSepIndex+", nextSepIndex="+nextSepIndex+", equalsIndex="+equalsIndex);
            // if we do not find one or only after the end of this feature string,
            // make a fuss about it
            if (equalsIndex < 0 || equalsIndex >= nextSepIndex) {
              throw new GateRuntimeException(
                      "Not a proper feature=value in gazetteer list " + listFileName
                      + " line " + lines + "\nlooking at " + line.substring(lastSepIndex, nextSepIndex)
                      + " lastSepIndex is " + lastSepIndex
                      + " nextSepIndex is " + nextSepIndex
                      + " equals at " + equalsIndex);
            }
            // add the key/value to the features string array: 
            // key to even positions, starting with 0, value to uneven starting with 1 
            nrFeatures++;
            featureBuffer[nrFeatures * 2 - 2] = line.substring(lastSepIndex + 1, equalsIndex);
            featureBuffer[nrFeatures * 2 - 1] = line.substring(equalsIndex + 1, nextSepIndex);
            lastSepIndex = nextSepIndex;
          } while (nextSepIndex < line.length());
          if (nrFeatures > 0) {
            entryFeatures = new String[nrFeatures * 2];
            System.arraycopy(featureBuffer, 0, entryFeatures, 0, entryFeatures.length);
          } else {
            entryFeatures = new String[0];
          }
        }
      } // have separator 
      // entry Features are passed as null if there are no entry features
      addLookup(entry, infoIndex, entryFeatures);
    } // while
    listReader.close();
    //logger.info("DEBUG: lines read "+lines);
    logger.debug("Lines read: " + lines);
  }

  public void addLookup(String text, int listInfoIndex, String[] entryFeatures) {
    // 1) instead of translating every character that is not within a word
    // on the fly when adding states, first normalize the text string and then
    // trim it. If the resulting word is empty, skip the whole processing because
    // the original consisted only of characters that are not word characters!
    // 2) if something remains and we want not exact case matching, convert
    // the whole string to both only upper and only lower case first, then
    // compare the lengths. If the lengths differ, add both in addition to
    // the original!

    String textNormalized = text.trim();
    // convert anything that is a sequence of whitespace to a single space
    // WAS: textNormalized = textNormalized.replaceAll("  +", " ");
    textNormalized = ws_pattern.matcher(textNormalized).replaceAll(" ");
    if (textNormalized.isEmpty()) {
      //logger.info("Ignoring, is empty");
      return;
    }

    // TODO: at some point this should get changed to allow for both totally
    // ignoring case (as now) and for matching either the original or a 
    // case-normalization (in the runtime). This would also need a setting
    // for specifying what the case normalization should be (e.g. UPPERCASE).
    // For now, we always normalize to upper case when case is ignored. 
    // The gazetteer should contain lowercase or firstCaseUpper words, but
    // better not ALLCAPS in order for lower case characters which get mapped
    // to two characters in uppercase to be mapped correctly.
    // For these special cases, we add two UPPERCASE normalizations:
    // the one with the two characters and the one where the char.toUpperCase 
    // is used.
    if (!caseSensitive) {
      String textNormalizedUpper = textNormalized.toUpperCase(caseConversionLocale);
      if (textNormalizedUpper.length() != textNormalized.length()) {
        gazStore.addLookup(textNormalizedUpper, listInfoIndex, entryFeatures);
        char[] textChars2 = new char[textNormalized.length()];
        for (int i = 0; i < textNormalized.length(); i++) {
          textChars2[i] = Character.toUpperCase(textNormalized.charAt(i));
        }
        gazStore.addLookup(new String(textChars2), listInfoIndex, entryFeatures);
      } else {
        // if both version are of the same length, it is sufficient to add the 
        // upper case version
        gazStore.addLookup(textNormalizedUpper, listInfoIndex, entryFeatures);
      }
    } else {
      gazStore.addLookup(textNormalized, listInfoIndex, entryFeatures);
    }

  } // addLookup

  /**
   * For a given lookups iterator, return a list of feature maps filled with the
   * information from those lookups.
   *
   * @param lookups
   * @return
   */
  public List<FeatureMap> lookups2FeatureMaps(Iterator<Lookup> lookups) {
    List<FeatureMap> fms = new ArrayList<>();
    if (lookups == null) {
      return fms;
    }
    while (lookups.hasNext()) {
      FeatureMap fm = Factory.newFeatureMap();
      Lookup currentLookup = lookups.next();
      gazStore.addLookupListFeatures(fm, currentLookup);
      fm.put("_listnr", gazStore.getListInfoIndex(currentLookup));
      gazStore.addLookupEntryFeatures(fm, currentLookup);
      fms.add(fm);
    }
    return fms;
  }

  protected List<Action> actions;

  @Override
  public List<Action> getActions() {
    if (actions == null) {
      actions = new ArrayList<>();

      // Action 1: remove the gazbin file and re-initialize the gazetteer
      actions.add(
              new AbstractAction("Remove cache and re-initialize") {
        {
          putValue(SHORT_DESCRIPTION,
                  "Remove cache and re-initialize");
          putValue(GateConstants.MENU_PATH_KEY,
                  new String[]{"WTFISTHIS??????"});
        }
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent evt) {
          URL cfgURL = null;
          try {
            cfgURL = configFileURL.toURL();
          } catch (IOException ex) {
            ex.printStackTrace(System.err);
            System.err.println("Could not re-initialize, problem getting URL for " + configFileURL);
            return;
          }
          if (!UrlUtils.isFile(cfgURL)) {
            System.err.println("Could not re-initialize, not a file URL");
          }
          File configFile = gate.util.Files.fileFromURL(cfgURL);
          String configFileName = configFile.getAbsolutePath();
          String gazbinFileName = configFileName.replaceAll("(?:\\.def$|\\.defyaml)", "_"+getCacheKey()+".gazbin");
          if (configFileName.equals(gazbinFileName)) {
            throw new GateRuntimeException("Config file must have def or defyaml extension!");
          }
          File gazbinFile = new File(gazbinFileName);
          gazbinFile.delete();
          try {
            reInit();
          } catch (ResourceInstantiationException ex) {
            throw new GateRuntimeException("Re-initialization failed", ex);
          }
        }
      });

    }
    return actions;
  }
} // ExtendedGazetteer

