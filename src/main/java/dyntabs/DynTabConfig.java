  package dyntabs;

  import java.util.List;

  /**
   * Abstract configuration class that defines which tabs are opened at startup
   * and the maximum number of tabs allowed.
   *
   * <p>To use DynTabs, create an {@code @ApplicationScoped} subclass of this class
   * in your application. For example, given a {@code HomeBean} annotated with
   * {@code @DynTab}:</p>
   *
   * <pre>
   * {@code
   * @Named
   * @TabScoped
   * @DynTab(name = "HomeDynTab",
   *         title = "Welcome",
   *         includePage = "/WEB-INF/include/home/home.xhtml",
   *         closeable = false)
   * public class HomeBean implements DyntabBeanInterface {
   *     // ...
   * }
   * }
   * </pre>
   *
   * <p>Override {@link #getInitialTabNames()} to specify which tabs open on startup:</p>
   *
   * <pre>
   * {@code
   * @ApplicationScoped
   * public class MyAppTabConfig extends DynTabConfig {
   *
   *     @Override
   *     public List<String> getInitialTabNames() {
   *         return List.of("HomeDynTab");
   *     }
   *
   *     @Override
   *     public int getMaxNumberOfTabs() {
   *         return 7;
   *     }
   * }
   * }
   * </pre>
   *
   * <p><b>Startup call chain for initial tabs:</b></p>
   * <ol>
   *   <li>CDI bootstrap: {@link dyntabs.annotation.DynTabDiscoveryExtension} scans {@code @DynTab}
   *       annotations and stores discovered tabs</li>
   *   <li>User opens the page: {@link DynTabTracker} is created ({@code @ViewScoped})</li>
   *   <li>{@link DynTabTracker#init()} calls {@code config.getInitialTabNames()} to get
   *       the list of tab names (e.g. {@code ["HomeDynTab"]})</li>
   *   <li>For each name, calls {@link DynTabRegistry#createTab(String)} which invokes the
   *       registered Supplier to create a fresh DynTab instance with all annotation data</li>
   *   <li>The tab gets an ID (r0), is set to active, and its CDI bean is resolved</li>
   * </ol>
   *
   * @author DynTabs
   * @see DynTabRegistry
   * @see DynTabTracker
   */
  public abstract class DynTabConfig {

      /**
       * Returns the names of tabs to open when the application starts.
       * These tabs must be previously registered in {@link DynTabRegistry}
       * (either via {@code @DynTab} annotation or manual {@code register()} call).
       *
       * @return list of registered tab names (e.g. {@code List.of("HomeDynTab", "DashboardDynTab")})
       */
      public abstract List<String> getInitialTabNames();

      /**
       * Returns the maximum number of tabs that can be open simultaneously.
       * Default is 7. Override to change.
       *
       * @return the maximum number of open tabs
       */
      public int getMaxNumberOfTabs() {
          return 7;
      }
  }