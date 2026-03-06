package dyntabs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynTabRegistryTest {

    private DynTabRegistry registry;

    /**
     * Creates a fresh DynTabRegistry (without calling init(), which depends on CDI).
     * Tests register tabs manually using register() and Supplier lambdas.
     */
    @BeforeEach
    void setUp() {
        registry = new DynTabRegistry();
    }

    // ---------------------------------------------------------------
    // register() + hasTab()
    // ---------------------------------------------------------------

    /**
     * After registering a tab, hasTab should return true for that name.
     */
    @Test
    void register_thenHasTab_returnsTrue() {
        registry.register("UsersDynTab", () -> {
            DynTab tab = new DynTab();
            tab.setTitle("Users");
            return tab;
        });

        assertThat(registry.hasTab("UsersDynTab")).isTrue();
    }

    /**
     * hasTab should return false for a name that was never registered.
     */
    @Test
    void hasTab_unregistered_returnsFalse() {
        assertThat(registry.hasTab("NonExistentDynTab")).isFalse();
    }

    // ---------------------------------------------------------------
    // createTab()
    // ---------------------------------------------------------------

    /**
     * createTab should invoke the registered Supplier and return a fresh DynTab instance.
     */
    @Test
    void createTab_returnsNewInstance() {
        registry.register("ProductsDynTab", () -> {
            DynTab tab = new DynTab();
            tab.setTitle("Products");
            tab.setIncludePage("/WEB-INF/include/products.xhtml");
            tab.setUniqueIdentifier("Products");
            tab.setCloseable(true);
            return tab;
        });

        DynTab tab = registry.createTab("ProductsDynTab");

        assertThat(tab).isNotNull();
        assertThat(tab.getTitle()).isEqualTo("Products");
        assertThat(tab.getIncludePage()).isEqualTo("/WEB-INF/include/products.xhtml");
        assertThat(tab.getUniqueIdentifier()).isEqualTo("Products");
        assertThat(tab.isCloseable()).isTrue();
    }

    /**
     * Each call to createTab should return a new, independent DynTab instance
     * (Supplier is invoked each time).
     */
    @Test
    void createTab_eachCallReturnsNewInstance() {
        registry.register("TestDynTab", () -> {
            DynTab tab = new DynTab();
            tab.setTitle("Test");
            return tab;
        });

        DynTab first = registry.createTab("TestDynTab");
        DynTab second = registry.createTab("TestDynTab");

        assertThat(first).isNotSameAs(second);
    }

    /**
     * createTab should throw IllegalArgumentException for an unregistered tab name.
     */
    @Test
    void createTab_unregistered_throwsException() {
        assertThatThrownBy(() -> registry.createTab("UnknownDynTab"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UnknownDynTab");
    }

    // ---------------------------------------------------------------
    // register() overwrite behavior
    // ---------------------------------------------------------------

    /**
     * Registering a tab with the same name twice should overwrite the first definition.
     */
    @Test
    void register_sameName_overwritesPrevious() {
        registry.register("OverwriteDynTab", () -> {
            DynTab tab = new DynTab();
            tab.setTitle("Original");
            return tab;
        });
        registry.register("OverwriteDynTab", () -> {
            DynTab tab = new DynTab();
            tab.setTitle("Replaced");
            return tab;
        });

        DynTab tab = registry.createTab("OverwriteDynTab");
        assertThat(tab.getTitle()).isEqualTo("Replaced");
    }

    // ---------------------------------------------------------------
    // Supplier with parameters (simulates @DynTab parameter parsing)
    // ---------------------------------------------------------------

    /**
     * Verifies that a Supplier can set parsed "key=value" parameters on the DynTab,
     * including boolean conversion for "true"/"false" values.
     * This simulates the logic in registerDiscoveredTab().
     */
    @Test
    void register_withParameterParsing_setsParametersCorrectly() {
        registry.register("ParamDynTab", () -> {
            DynTab tab = new DynTab();
            tab.setTitle("Param Tab");

            // Simulate parameter parsing from @DynTab(parameters = {"mode=edit", "readOnly=false"})
            String[] rawParams = {"mode=edit", "readOnly=false", "enabled=true"};
            Map<String, Object> params = new java.util.HashMap<>();
            for (String param : rawParams) {
                int eqPos = param.indexOf('=');
                if (eqPos > 0) {
                    String key = param.substring(0, eqPos).trim();
                    String value = param.substring(eqPos + 1).trim();
                    if ("true".equalsIgnoreCase(value)) {
                        params.put(key, Boolean.TRUE);
                    } else if ("false".equalsIgnoreCase(value)) {
                        params.put(key, Boolean.FALSE);
                    } else {
                        params.put(key, value);
                    }
                }
            }
            tab.setParameters(params);
            return tab;
        });

        DynTab tab = registry.createTab("ParamDynTab");
        Map params = tab.getParameters();

        assertThat(params.get("mode")).isEqualTo("edit");
        assertThat(params.get("readOnly")).isEqualTo(Boolean.FALSE);
        assertThat(params.get("enabled")).isEqualTo(Boolean.TRUE);
    }

    /**
     * Verifies that a Supplier correctly stores the CDI bean class on the DynTab
     * without resolving it (bean resolution happens later when TabScope is active).
     */
    @Test
    void register_withCdiBeanClass_storesClassWithoutResolving() {
        registry.register("BeanDynTab", () -> {
            DynTab tab = new DynTab();
            tab.setTitle("Bean Tab");
            tab.setCdiBeanClass(String.class); // placeholder class for testing
            return tab;
        });

        DynTab tab = registry.createTab("BeanDynTab");

        assertThat(tab.getCdiBeanClass()).isEqualTo(String.class);
        // cdiBean should NOT be resolved yet (no CDI container in unit test)
        assertThat(tab.getCdiBeanDirect()).isNull();
    }

    // ---------------------------------------------------------------
    // Multiple registrations
    // ---------------------------------------------------------------

    /**
     * Multiple tabs can be registered and each one is independently accessible.
     */
    @Test
    void register_multipleTabs_allAccessible() {
        registry.register("Tab1DynTab", () -> { DynTab t = new DynTab(); t.setTitle("T1"); return t; });
        registry.register("Tab2DynTab", () -> { DynTab t = new DynTab(); t.setTitle("T2"); return t; });
        registry.register("Tab3DynTab", () -> { DynTab t = new DynTab(); t.setTitle("T3"); return t; });

        assertThat(registry.hasTab("Tab1DynTab")).isTrue();
        assertThat(registry.hasTab("Tab2DynTab")).isTrue();
        assertThat(registry.hasTab("Tab3DynTab")).isTrue();
        assertThat(registry.createTab("Tab2DynTab").getTitle()).isEqualTo("T2");
    }
}
