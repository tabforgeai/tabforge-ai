package dyntabs.annotation;

import dyntabs.annotation.DynTabDiscoveryExtension.DiscoveredTab;
import dyntabs.interfaces.DyntabBeanInterface;
import dyntabs.ApplicationCDIEvent;
import dyntabs.DynTabCDIEvent;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynTabDiscoveryExtensionTest {

    private DynTabDiscoveryExtension extension;

    /**
     * Creates a fresh extension and clears the static discoveredTabs list
     * via reflection to prevent interference between tests.
     */
    @BeforeEach
    void setUp() throws Exception {
        extension = new DynTabDiscoveryExtension();

        // Clear the static discoveredTabs list (no public API for this)
        Field field = DynTabDiscoveryExtension.class.getDeclaredField("discoveredTabs");
        field.setAccessible(true);
        ((List<?>) field.get(null)).clear();
    }

    // ---------------------------------------------------------------
    // Helper: create a mocked ProcessAnnotatedType for a given class
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> ProcessAnnotatedType<T> mockPAT(Class<T> clazz) {
        ProcessAnnotatedType<T> pat = mock(ProcessAnnotatedType.class);
        AnnotatedType<T> annotatedType = mock(AnnotatedType.class);
        when(pat.getAnnotatedType()).thenReturn(annotatedType);
        when(annotatedType.getJavaClass()).thenReturn(clazz);
        return pat;
    }

    // ---------------------------------------------------------------
    // Test bean classes with @DynTab annotations
    // ---------------------------------------------------------------

    /** A valid bean with a single @DynTab annotation implementing DyntabBeanInterface. */
    @DynTab(name = "UsersDynTab", title = "Users", includePage = "/WEB-INF/include/users.xhtml")
    static class ValidSingleTabBean extends StubDyntabBean {}

    /** A valid bean with @DynTab that has all optional fields set. */
    @DynTab(name = "OrdersDynTab", title = "Orders", includePage = "/WEB-INF/include/orders.xhtml",
            closeable = false, uniqueIdentifier = "CustomOrders",
            parameters = {"mode=edit", "readOnly=false", "maxRows=50"})
    static class FullConfigTabBean extends StubDyntabBean {}

    /** A bean with two @DynTab annotations (uses @Repeatable). */
    @DynTab(name = "AktiDynTab", title = "Akti",
            includePage = "/WEB-INF/include/akti.xhtml", parameters = {"popisAkata=false"})
    @DynTab(name = "PopisAkataDynTab", title = "Popis akata",
            includePage = "/WEB-INF/include/akti.xhtml", parameters = {"popisAkata=true"})
    static class RepeatableTabBean extends StubDyntabBean {}

    /** A bean with @DynTab but NOT implementing DyntabBeanInterface (should still be discovered, with warning). */
    @DynTab(name = "BadDynTab", title = "Bad", includePage = "/WEB-INF/include/bad.xhtml")
    static class NonInterfaceBean {}

    // ---------------------------------------------------------------
    // processDynTabAnnotation — single annotation
    // ---------------------------------------------------------------

    /**
     * A class with a single @DynTab should produce exactly one DiscoveredTab entry.
     */
    @Test
    void processDynTabAnnotation_singleAnnotation_discoversOneTab() {
        extension.processDynTabAnnotation(mockPAT(ValidSingleTabBean.class));

        List<DiscoveredTab> tabs = DynTabDiscoveryExtension.getDiscoveredTabs();
        assertThat(tabs).hasSize(1);

        DiscoveredTab dt = tabs.get(0);
        assertThat(dt.getAnnotation().name()).isEqualTo("UsersDynTab");
        assertThat(dt.getAnnotation().title()).isEqualTo("Users");
        assertThat(dt.getAnnotation().includePage()).isEqualTo("/WEB-INF/include/users.xhtml");
        assertThat(dt.getAnnotation().closeable()).isTrue(); // default
        assertThat(dt.getBeanClass()).isEqualTo(ValidSingleTabBean.class);
    }

    /**
     * A @DynTab with all optional fields set should have them all captured in DiscoveredTab.
     */
    @Test
    void processDynTabAnnotation_fullConfig_capturesAllFields() {
        extension.processDynTabAnnotation(mockPAT(FullConfigTabBean.class));

        DiscoveredTab dt = DynTabDiscoveryExtension.getDiscoveredTabs().get(0);
        assertThat(dt.getAnnotation().name()).isEqualTo("OrdersDynTab");
        assertThat(dt.getAnnotation().closeable()).isFalse();
        assertThat(dt.getAnnotation().uniqueIdentifier()).isEqualTo("CustomOrders");
        assertThat(dt.getAnnotation().parameters()).containsExactly("mode=edit", "readOnly=false", "maxRows=50");
    }

    // ---------------------------------------------------------------
    // processDynTabAnnotation — @Repeatable (multiple annotations)
    // ---------------------------------------------------------------

    /**
     * A class with two @DynTab annotations should produce two DiscoveredTab entries,
     * both pointing to the same bean class but with different annotation data.
     */
    @Test
    void processDynTabAnnotation_repeatable_discoversBothTabs() {
        extension.processDynTabAnnotation(mockPAT(RepeatableTabBean.class));

        List<DiscoveredTab> tabs = DynTabDiscoveryExtension.getDiscoveredTabs();
        assertThat(tabs).hasSize(2);

        // Both should reference the same bean class
        assertThat(tabs.get(0).getBeanClass()).isEqualTo(RepeatableTabBean.class);
        assertThat(tabs.get(1).getBeanClass()).isEqualTo(RepeatableTabBean.class);

        // But different names and parameters
        assertThat(tabs.get(0).getAnnotation().name()).isEqualTo("AktiDynTab");
        assertThat(tabs.get(1).getAnnotation().name()).isEqualTo("PopisAkataDynTab");
        assertThat(tabs.get(0).getAnnotation().parameters()).containsExactly("popisAkata=false");
        assertThat(tabs.get(1).getAnnotation().parameters()).containsExactly("popisAkata=true");
    }

    // ---------------------------------------------------------------
    // processDynTabAnnotation — class without DyntabBeanInterface
    // ---------------------------------------------------------------

    /**
     * A class with @DynTab but not implementing DyntabBeanInterface should still be
     * discovered (the extension logs a warning but does not reject it).
     */
    @Test
    void processDynTabAnnotation_classWithoutInterface_stillDiscovered() {
        extension.processDynTabAnnotation(mockPAT(NonInterfaceBean.class));

        List<DiscoveredTab> tabs = DynTabDiscoveryExtension.getDiscoveredTabs();
        assertThat(tabs).hasSize(1);
        assertThat(tabs.get(0).getAnnotation().name()).isEqualTo("BadDynTab");
    }

    // ---------------------------------------------------------------
    // Multiple calls accumulate
    // ---------------------------------------------------------------

    /**
     * Processing multiple classes should accumulate all discovered tabs.
     */
    @Test
    void processDynTabAnnotation_multipleCalls_accumulateTabs() {
        extension.processDynTabAnnotation(mockPAT(ValidSingleTabBean.class));
        extension.processDynTabAnnotation(mockPAT(FullConfigTabBean.class));
        extension.processDynTabAnnotation(mockPAT(RepeatableTabBean.class));

        // 1 + 1 + 2 = 4
        assertThat(DynTabDiscoveryExtension.getDiscoveredTabs()).hasSize(4);
    }

    // ---------------------------------------------------------------
    // getDiscoveredTabs() returns unmodifiable list
    // ---------------------------------------------------------------

    /**
     * getDiscoveredTabs should return an unmodifiable list to prevent external modification.
     */
    @Test
    void getDiscoveredTabs_returnsUnmodifiableList() {
        extension.processDynTabAnnotation(mockPAT(ValidSingleTabBean.class));

        List<DiscoveredTab> tabs = DynTabDiscoveryExtension.getDiscoveredTabs();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> tabs.add(new DiscoveredTab(null, null)));
    }

    /**
     * Before any processing, getDiscoveredTabs should return an empty list.
     */
    @Test
    void getDiscoveredTabs_initially_returnsEmptyList() {
        assertThat(DynTabDiscoveryExtension.getDiscoveredTabs()).isEmpty();
    }

    // ---------------------------------------------------------------
    // DiscoveredTab inner class
    // ---------------------------------------------------------------

    /**
     * DiscoveredTab should correctly store and return the annotation and bean class.
     */
    @Test
    void discoveredTab_storesAnnotationAndClass() {
        extension.processDynTabAnnotation(mockPAT(ValidSingleTabBean.class));

        DiscoveredTab dt = DynTabDiscoveryExtension.getDiscoveredTabs().get(0);

        assertThat(dt.getAnnotation()).isNotNull();
        assertThat(dt.getAnnotation().name()).isEqualTo("UsersDynTab");
        assertThat(dt.getBeanClass()).isEqualTo(ValidSingleTabBean.class);
    }

    // ---------------------------------------------------------------
    // Stub implementation of DyntabBeanInterface for test beans
    // ---------------------------------------------------------------

    static abstract class StubDyntabBean implements DyntabBeanInterface {
        public void init() {}
        public void callAccessPointMethod() {}
        public void callMethodActivity(String o, String n) {}
        public void callViewActivity(String v) {}
        public void observeDynTabEvent(DynTabCDIEvent e) {}
        public void setActive(boolean a) {}
        public boolean getActive() { return false; }
        public void callExitPointMethod() {}
        public void observeApplicationEvent(ApplicationCDIEvent e) {}
        public void setDynTab(dyntabs.DynTab dt) {}
        public dyntabs.DynTab getDynTab() { return null; }
        public Map getParameters() { return null; }
        public String getUniqueIdentifier() { return null; }
        public String getDynTabId() { return null; }
    }
}
