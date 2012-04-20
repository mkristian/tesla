package org.sonatype.maven.polyglot.atom.parsing;

import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.ModelSource;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.sonatype.maven.polyglot.atom.parsing.Token.Kind;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

/**
 * Parses the atom token stream into an internal model, which can be emitted as a Maven model.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class AtomParser {
  // TODO(dhanji): Replace with proper logging/console out
  private final Logger log = Logger.getLogger(AtomParser.class.getName());
  private final List<Token> tokens;
  // This is the ultimate source where the model came from. We can use this to help the user understand problems in the parsing of the
  // model and help them make corrections.
  private final ModelSource modelSource;

  // Parser state. (current index)
  private int i = 0;

  public AtomParser(ModelSource modelSource, List<Token> tokens) {
    this.tokens = tokens;
    this.modelSource = modelSource;
  }

  private void parseException(String message, Throwable t) {
    throw new RuntimeException("Error parsing " + modelSource.getLocation() + ": " + message, t);
  }

  private void parseException(String message) {
    throw new RuntimeException("Error parsing " + modelSource.getLocation() + ": " + message);
  }

  public Project parse() {
    chewEols();
    Repositories repositories = repositories();
    if (null == repositories) {
      // don't really need to log this. jvz.
      // log.warning("No repositories specified in atom file, defaulting to Maven Central.");
    }

    chewEols();

    return project(repositories);
  }

  /**
   * Parsing rule for a single project build definition.
   *
   * project := 'project' STRING (AT URL)? ('as' PACKAGING)? EOL
   *            (idFragment COLON list EOL)+
   */
  private Project project(Repositories repositories) {
    if (match(Token.Kind.PROJECT) == null) {
      return null;
    }

    List<Token> signature = match(Token.Kind.STRING);
    if (null == signature) {
      log.severe("Expected string describing project after 'project'.");
    }

    String projectDescription = signature.get(0).value;
    String projectUrl = null;

    signature = match(Token.Kind.AT, Token.Kind.STRING);
    if (null != signature) {
      projectUrl = signature.get(1).value;
    }

    List<Token> packagingTokens = match(Kind.PACKAGING, Kind.IDENT);
    String packaging = null;
    if (null != packagingTokens) {
      packaging = packagingTokens.get(1).value;
    }

    if (match(Token.Kind.EOL) == null) {
      log.severe("Expected end of line after project declaration");
      return null;
    }

    // id definition
    indent();
    if (match(Token.Kind.ID) == null) {
      log.severe("Expected 'id' after project declaration");
      return null;
    }

    // Now expect a colon.
    if (match(Token.Kind.COLON) == null) {
      log.severe("Expected ':' after 'id'");
      return null;
    }

    Id projectId = id();

    // parent
    chewEols();
    chewIndents();

    Parent parent = parent();

    // packaging
    chewEols();
    chewIndents();

    // srcs
    chewEols();
    Map<String, String> dirs = srcs();

    // properties
    chewEols();
    chewIndents();
    List<Property> properties = properties(Token.Kind.PROPS);

    // dependencies
    chewEols();
    chewIndents();
    List<Id> overrides = dependencies(Token.Kind.OVERRIDES, false);

    // dependencies
    chewEols();
    chewIndents();
    List<Id> deps = dependencies(Token.Kind.DEPS, true);

    // modules
    chewEols();
    chewIndents();
    List<String> modules = modules();

    ScmElement scm = scm();

    // modules
    chewEols();
    chewIndents();
    List<Plugin> plugins = plugins();

    return new Project(projectId,
        parent,
        packaging,
        properties,
        repositories,
        projectDescription,
        projectUrl,
        overrides,
        deps,
        modules,
        plugins,
        dirs,
        scm);
  }

  private ScmElement scm() {
    chewIndents();
    if (match(Token.Kind.SCM, Token.Kind.COLON, Token.Kind.LBRACKET) == null) {
      return null; // no scm element
    }

    chewEols();
    chewIndents();

    String connection = null, developerConnection = null, url = null;
    List<Token> ident;
    while ((ident = match(Token.Kind.IDENT)) != null) {
      String label = ident.get(0).value;

      if (match(Token.Kind.COLON) == null) {
        parseException("Expected : after label");
      }

      List<Token> valueToken = match(Token.Kind.STRING);
      if (null == valueToken) {
        parseException("Expected String after :");
      }
      String value = valueToken.get(0).value;

      if ("connection".equals(label))
        connection = value;
      else if ("developerConnection".equals(label))
        developerConnection = value;
      else if ("url".equals(label))
        url = value;

      chewEols();
      chewIndents();
    }

    if (match(Token.Kind.RBRACKET) == null) {
      parseException("Expected ] after srcs list");
    }

    return new ScmElement(connection, developerConnection, url);
  }

  /**
   * Custom directory structure for maven builds.
   */
  private Map<String, String> srcs() {
    indent();
    if (match(Token.Kind.SRCS, Token.Kind.COLON, Token.Kind.LBRACKET) == null) {
      return null; // No dirs!
    }

    chewEols();
    chewIndents();
    List<Token> aMatch = match(Token.Kind.IDENT);
    if (aMatch == null) {
      parseException("Expected 'src' or 'test'");
    }

    String srcDir = null;
    if ("src".equals(aMatch.get(0).value)) {
      if (null == match(Token.Kind.COLON)) {
        parseException("Expected : after src");
      }

      List<Token> srcDirToken = match(Token.Kind.STRING);
      if (null == srcDirToken) {
        parseException("Expected string after src:");
      }

      srcDir = srcDirToken.get(0).value;
    }

    aMatch = match(Token.Kind.IDENT);

    String testDir = null;
    if (null != aMatch && "test".equals(aMatch.get(0).value)) {
      if (null == match(Token.Kind.COLON)) {
        parseException("Expected : after test");
      }

      List<Token> testDirToken = match(Token.Kind.STRING);
      if (null == testDirToken) {
        parseException("Expected string after test:");
      }

      testDir = testDirToken.get(0).value;
    }

    Map<String, String> dirs = new HashMap<String, String>();
    // Strip quotes and store.
    if (null != srcDir)
      dirs.put("src", srcDir.substring(1, srcDir.length() - 1));
    if (null != testDir)
      dirs.put("test", testDir.substring(1, testDir.length() - 1));

    if (match(Token.Kind.RBRACKET) == null) {
      parseException("Expected ] after srcs list");
    }

    return dirs;
  }

  /**
   * Dependencies of a project. The real meat of it.
   */
  private List<Id> dependencies(Token.Kind kind, boolean allowNullVersion) {
    indent();
    if (match(kind, Token.Kind.COLON, Token.Kind.LBRACKET) == null) {
      return null; // no deps.
    }
    List<Id> deps = new ArrayList<Id>();

    chewEols();
    chewIndents();

    // Slurp up the dep ids.
    Id id;
    while ((id = id(allowNullVersion)) != null) {
      // Optional additional params at the end.
      String classifier = classifier();
      if (null != classifier) {
        id.setClassifier(classifier);
      }

      chewEols();
      chewIndents();
      deps.add(id);
    }

    if (match(Token.Kind.RBRACKET) == null) {
      // ERROR!
      parseException("Expected ]");
    }

    return deps;
  }

  /**
   * Additional plugins and their configuration.
   */
  private List<Plugin> plugins() {
    List<Plugin> plugins = new ArrayList<Plugin>();

    chewEols();
    Plugin plugin;
    while ((plugin = plugin()) != null) {
      plugins.add(plugin);

      chewEols();
    }

    return plugins;
  }

  private Plugin plugin() {
    if (match(Kind.PLUGIN) == null)
      return null;

    if (match(Kind.EOL) == null) {
      parseException("Expected newline after 'plugin' keyword");
    }

    Plugin plugin = new Plugin();
    List<Token> propKey;

    chewIndents();
    if (match(Kind.ID, Kind.COLON) == null) {
      log.severe("Plugin declaration is missing an 'id' tag");
      return null;
    }

    Id pluginId = id();
    if (pluginId == null) {
      log.severe("Plugin id declaration malformed");
      return null;
    }

    if (match(Kind.EOL) == null) {
      log.severe("Expected newline after plugin id declaration");
      return null;
    }

    // Dont forget to set the id properties.
    plugin.setGroupId(pluginId.getGroup());
    plugin.setArtifactId(pluginId.getArtifact());
    plugin.setVersion(pluginId.getVersion());

    Map<String, Object> config;
    if ((config = configurationMap()) == null)
      return plugin;

    // Transform the parsed config map into maven's XPP3 Dom thing.
    plugin.setConfiguration(toXpp3DomTree("configuration", config));

    return plugin;
  }

  private Xpp3Dom toXpp3DomTree(String name, Map<String, Object> config) {
    Xpp3Dom xConfig = new Xpp3Dom(name);
    for (Entry<String, Object> entry : config.entrySet()) {
      if (entry.getValue() instanceof String) {
        Xpp3Dom node = new Xpp3Dom(entry.getKey());
        node.setValue(entry.getValue().toString());

        xConfig.addChild(node);
      } else {
        @SuppressWarnings("unchecked")    // Guaranteed by #configurationMap()
        Map<String, Object> childMap = (Map<String, Object>) entry.getValue();

        // Recurse.
        Xpp3Dom child = toXpp3DomTree(entry.getKey(), childMap);
        xConfig.addChild(child);
      }
    }
    return xConfig;
  }

  private Map<String, Object> configurationMap() {
    Map<String, Object> config = new LinkedHashMap<String, Object>();
    List<Token> propKey;

    chewIndents();
    while ((propKey = match(Kind.IDENT, Kind.COLON)) != null) {
      // Match the rest of the line as either an atom or as another set of properties.
      String atom = idFragment();

      if (atom == null) {
        List<Token> tokens = match(Kind.STRING);
        if (tokens != null)
          atom = tokens.get(0).value;
      }

      if (atom != null) {
        atom = atom.trim();
        // Strip quotes.
        if (atom.startsWith("\"") && atom.endsWith("\""))
          atom = atom.substring(1, atom.length() - 1);

        config.put(propKey.get(0).value, atom);

        // eol here is optional.
        match(Kind.EOL);
      } else {
        // This is a multilevel thing, recurse!
        if (match(Kind.LBRACKET) != null) {
          Map<String, Object> childProps = configurationMap();
          if (match(Kind.RBRACKET) == null)
            parseException("Expected ']' after configuration properties");

          // stash into parent map.
          config.put(propKey.get(0).value, childProps);

        } else {
          // ignore. Later we can force-parse this as a string.
          log.warning("Unknown element type in plugin declaration");
          return null;
        }
      }

      chewIndents();
    }

    return config;
  }

  private List<Property> properties(Token.Kind kind) {

    indent();
    if (match(kind, Token.Kind.COLON, Token.Kind.LBRACKET) == null) {
      return null; // no properties.
    }
    List<Property> properties = new ArrayList<Property>();

    chewEols();
    chewIndents();

    Property p;
    while ((p = property()) != null) {
      chewEols();
      chewIndents();
      properties.add(p);
    }

    if (match(Token.Kind.RBRACKET) == null) {
      // ERROR!
      parseException("Expected ]");
    }

    return properties;
  }

  private List<String> modules() {

    indent();
    if (match(Token.Kind.MODULES, Token.Kind.COLON, Token.Kind.LBRACKET) == null) {
      return null; // no properties.
    }
    List<String> modules = new ArrayList<String>();

    chewEols();
    chewIndents();

    String module;
    while ((module = idFragment()).length() != 0) {
      chewEols();
      chewIndents();
      modules.add(module);
    }

    if (match(Token.Kind.RBRACKET) == null) {
      // ERROR!
      parseException("Expected ]");
    }

    return modules;
  }

  /**
   * classifier := LPAREN IDENT RPAREN
   */
  private String classifier() {
    if (match(Token.Kind.LPAREN) == null) {
      return null;
    }

    List<Token> classifier = match(Token.Kind.IDENT);
    if (classifier == null) {
      log.severe("Expected identifier after '(' in classifier clause");
      return null;
    }

    if (match(Token.Kind.RPAREN) == null) {
      log.severe("Expected ')' in classifier clause before end of line");
      return null;
    }

    return classifier.get(0).value;
  }

  private Property property() {

    String key = idFragment();
    if (key == null) {
      return null;
    }

    // Now expect a colon.
    if (match(Token.Kind.COLON) == null) {
      return null;
    }

    String value = idFragment();
    if (value == null) {
      return null;
    }

    return new Property(key, value);
  }

  /**
   * Id of a project definition.
   * <p/>
   * id := IDENT (DOT IDENT)* COLON IDENT (COLON IDENT)? EOL
   */
  private Id id() {
    return id(false);
  }

  private Id id(boolean allowNullVersion) {

    String groupId = idFragment();
    if (groupId == null) {
      return null;
    }

    // Now expect a colon.
    if (match(Token.Kind.COLON) == null) {
      return null;
    }

    String artifactId = idFragment();
    if (artifactId == null) {
      return null;
    }

    // Now expect a colon.
    String version;
    if (match(Token.Kind.COLON) == null && !allowNullVersion) {
      return null;
    } else {
      version = idFragment();
      if (version == null) {
        return null;
      }
    }

    return new Id(groupId, artifactId, StringUtils.isEmpty(version) ? null : version);
  }

  private Parent parent() {
    if (match(Kind.PARENT) == null)
      return null;

    if (match(Kind.COLON) == null) {
      log.severe("Expected ':' after 'inherits'");
      return null;
    }

    Id parentId = id(true);

    if (parentId == null) {
      log.severe("Expected complete artifact identifier in 'parent' clause");
      return null;
    }

    String relativePath = "../pom.atom";
    if (match(Token.Kind.COLON) != null) {
      relativePath = relativePath();
      if (relativePath == null) {
        return null;
      }
    }

    Parent parent = new Parent();
    parent.setGroupId(parentId.getGroup());
    parent.setArtifactId(parentId.getArtifact());
    parent.setVersion(parentId.getVersion());
    parent.setRelativePath(relativePath);

    return parent;
  }

  //
  // ../atom.pom
  //
  private String relativePath() {
    StringBuilder fragment = new StringBuilder();
    if (match(Token.Kind.DOT) != null) {
      fragment.append(".");
    }
    if (match(Token.Kind.DOT) != null) {
      fragment.append(".");
    }

    List<Token> idFragment;
    while ((idFragment = match(Token.Kind.IDENT)) != null) {
      fragment.append(idFragment.get(0).value);
      if (match(Token.Kind.DOT) != null) {
        fragment.append('.');
      }
    }

    return fragment.toString();
  }

  private String idFragment() {
    StringBuilder fragment = new StringBuilder();
    Token idFragment;
    while ((idFragment = anyOf(
        Token.Kind.IDENT,
        Kind.PLUGIN,
        Kind.PROJECT,
        Kind.DEPS,
        Kind.SCM,
        Kind.SRCS,
        Kind.MODULES,
        Kind.ID,
        Kind.PACKAGING,
        Kind.PARENT,
        Kind.OVERRIDES,
        Kind.REPOSITORIES,
        Kind.PROPS
    )) != null) {
      fragment.append(idFragment.value);
      if (match(Token.Kind.DOT) != null) {
        fragment.append('.');
      }
      if (match(Token.Kind.DASH) != null) {
        fragment.append('-');
      }
    }

    // Nothing matched.
    if (fragment.length() == 0)
      return null;

    return fragment.toString();
  }

  /**
   * Optional repositories declaration at the top of the file.
   * <p/>
   * repositories := 'repositories' LEFT_WAVE STRING (COMMA STRING)*
   */
  private Repositories repositories() {
    if (match(Token.Kind.REPOSITORIES, Token.Kind.LEFT_WAVE) == null) {
      return null;
    }
    List<String> repoUrls = new ArrayList<String>();

    List<Token> repositories = match(Token.Kind.STRING);
    if (repositories == null) {
      // ERROR expected String.
      parseException("Error: expected URL string after 'respositories");
    }

    // Validate first URL...
    //noinspection ConstantConditions
    String url = repositories.get(0).value; // Strip ""
    repoUrls.add(validateUrl(url));

    while ((repositories = match(Token.Kind.COMMA)) != null) {
      chewEols();
      chewIndents();
      repositories = match(Token.Kind.STRING);
      chewEols();
      chewIndents();
      if (null != repositories) {
        url = validateUrl(repositories.get(0).value);

        repoUrls.add(url);
      }
    }

    return new Repositories(repoUrls);
  }

  private String validateUrl(String url) {
    url = url.substring(1, url.length() - 1);

    // Validate URL...
    try {
      new URL(url);
    } catch (MalformedURLException e) {
      parseException("Invalid URL: " + url, e);
    }
    return url;
  }

  // Production tools.

  private void indent() {
    if (match(Token.Kind.INDENT, Token.Kind.INDENT) != null) {
      // ERROR!
    }
  }

  private Token anyOf(Token.Kind... ident) {
    if (i >= tokens.size()) {
      return null;
    }
    for (Token.Kind kind : ident) {
      Token token = tokens.get(i);
      if (kind == token.kind) {
        i++;
        return token;
      }
    }

    // No match =(
    return null;
  }

  private List<Token> match(Token.Kind... ident) {
    int cursor = i;
    for (Token.Kind kind : ident) {

      // What we want is more than the size of the token stream.
      if (cursor >= tokens.size()) {
        return null;
      }

      Token token = tokens.get(cursor);
      if (token.kind != kind) {
        return null;
      }

      cursor++;
    }

    // Forward cursor in token stream to match point.
    int start = i;
    i = cursor;
    return tokens.subList(start, i);
  }

  private void chewEols() {
    // Chew up end-of-lines.
    while (match(Token.Kind.EOL) != null)
      ;
  }

  private void chewIndents() {
    // Chew up end-of-lines.
    while (match(Token.Kind.INDENT) != null)
      ;
  }
}
