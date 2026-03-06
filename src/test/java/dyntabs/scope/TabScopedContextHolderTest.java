package dyntabs.scope;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TabScopedContextHolderTest {

    private TabScopedContextHolder holder;
    private MockedStatic<FacesContext> facesContextMock;
    private FacesContext facesContext;
    private ExternalContext externalContext;

    /**
     * Sets up a mocked FacesContext that returns a controlled session ID ("test-session").
     * This allows testing methods that depend on FacesContext without a running JSF container.
     */
    @BeforeEach
    void setUp() {
        holder = TabScopedContextHolder.getInstance();

        facesContext = mock(FacesContext.class);
        externalContext = mock(ExternalContext.class);
        when(facesContext.getExternalContext()).thenReturn(externalContext);
        when(externalContext.getSessionId(false)).thenReturn("test-session");

        facesContextMock = mockStatic(FacesContext.class);
        facesContextMock.when(FacesContext::getCurrentInstance).thenReturn(facesContext);
    }

    /**
     * Cleans up mocked FacesContext, ThreadLocal state, and any beans stored during the test
     * to prevent interference between tests.
     */
    @AfterEach
    void tearDown() {
        facesContextMock.close();
        TabScopedContextHolder.clearCurrentTabId();
        holder.destroyAllBeansForSession("test-session");
        holder.destroyAllBeansForSession("other-session");
        holder.destroyAllBeansForSession("new-session");
    }

    // ---------------------------------------------------------------
    // ThreadLocal: setCurrentTabId / getCurrentTabId / clearCurrentTabId
    // ---------------------------------------------------------------

    /**
     * After setting a tab ID, getCurrentTabId should return that same value.
     */
    @Test
    void setCurrentTabId_thenGetReturnsIt() {
        TabScopedContextHolder.setCurrentTabId("r0");

        assertThat(TabScopedContextHolder.getCurrentTabId()).isEqualTo("r0");
    }

    /**
     * Before any tab ID is set, getCurrentTabId should return null.
     */
    @Test
    void getCurrentTabId_beforeSet_returnsNull() {
        assertThat(TabScopedContextHolder.getCurrentTabId()).isNull();
    }

    /**
     * After clearing, getCurrentTabId should return null again.
     */
    @Test
    void clearCurrentTabId_removesTheValue() {
        TabScopedContextHolder.setCurrentTabId("r0");
        TabScopedContextHolder.clearCurrentTabId();

        assertThat(TabScopedContextHolder.getCurrentTabId()).isNull();
    }

    // ---------------------------------------------------------------
    // Singleton
    // ---------------------------------------------------------------

    /**
     * getInstance() should always return the same singleton instance.
     */
    @Test
    void getInstance_alwaysReturnsSameInstance() {
        TabScopedContextHolder a = TabScopedContextHolder.getInstance();
        TabScopedContextHolder b = TabScopedContextHolder.getInstance();

        assertThat(a).isSameAs(b);
    }

    // ---------------------------------------------------------------
    // putBean / getBean
    // ---------------------------------------------------------------

    /**
     * After storing a bean with putBean, getBean should return the same instance.
     */
    @SuppressWarnings("unchecked")
    @Test
    void putBean_thenGetBean_returnsSameInstance() {
        Contextual<String> contextual = mock(Contextual.class);
        CreationalContext<String> cc = mock(CreationalContext.class);

        holder.putBean("r0", contextual, "myBeanInstance", cc);

        TabScopedContextHolder.BeanInstance<String> result = holder.getBean("r0", contextual);
        assertThat(result).isNotNull();
        assertThat(result.getInstance()).isEqualTo("myBeanInstance");
        assertThat(result.getCreationalContext()).isSameAs(cc);
    }

    /**
     * getBean should return null when no bean has been stored for the given tab.
     */
    @SuppressWarnings("unchecked")
    @Test
    void getBean_nonExistentTab_returnsNull() {
        Contextual<String> contextual = mock(Contextual.class);

        TabScopedContextHolder.BeanInstance<String> result = holder.getBean("nonexistent", contextual);

        assertThat(result).isNull();
    }

    /**
     * Beans stored in different tabs should be isolated from each other,
     * even when using the same Contextual type.
     */
    @SuppressWarnings("unchecked")
    @Test
    void putBean_differentTabs_beansAreIsolated() {
        Contextual<String> contextual = mock(Contextual.class);
        CreationalContext<String> cc = mock(CreationalContext.class);

        holder.putBean("r0", contextual, "beanInTab0", cc);
        holder.putBean("r1", contextual, "beanInTab1", cc);

        assertThat(holder.getBean("r0", contextual).getInstance()).isEqualTo("beanInTab0");
        assertThat(holder.getBean("r1", contextual).getInstance()).isEqualTo("beanInTab1");
    }

    // ---------------------------------------------------------------
    // getBeansForTab
    // ---------------------------------------------------------------

    /**
     * getBeansForTab should create an empty map for a new tab
     * (computeIfAbsent behavior).
     */
    @Test
    void getBeansForTab_newTab_returnsEmptyMap() {
        Map<Contextual<?>, TabScopedContextHolder.BeanInstance<?>> beans = holder.getBeansForTab("newTab");

        assertThat(beans).isEmpty();
    }

    // ---------------------------------------------------------------
    // hasTab
    // ---------------------------------------------------------------

    /**
     * hasTab should return true after a bean has been stored for that tab.
     */
    @SuppressWarnings("unchecked")
    @Test
    void hasTab_afterPutBean_returnsTrue() {
        Contextual<String> contextual = mock(Contextual.class);
        CreationalContext<String> cc = mock(CreationalContext.class);

        holder.putBean("r0", contextual, "instance", cc);

        assertThat(holder.hasTab("r0")).isTrue();
    }

    /**
     * hasTab should return false for a tab that was never used.
     */
    @Test
    void hasTab_unknownTab_returnsFalse() {
        assertThat(holder.hasTab("unknown")).isFalse();
    }

    // ---------------------------------------------------------------
    // destroyBeansForTab
    // ---------------------------------------------------------------

    /**
     * After destroying beans for a tab, getBean should return null
     * and the Contextual.destroy() method should have been called.
     */
    @SuppressWarnings("unchecked")
    @Test
    void destroyBeansForTab_removesTabAndCallsDestroy() {
        Contextual<String> contextual = mock(Contextual.class);
        CreationalContext<String> cc = mock(CreationalContext.class);
        holder.putBean("r0", contextual, "instance", cc);

        holder.destroyBeansForTab("r0");

        assertThat(holder.getBean("r0", contextual)).isNull();
        verify(contextual).destroy("instance", cc);
    }

    /**
     * Destroying a non-existent tab should not throw an exception.
     */
    @Test
    void destroyBeansForTab_nonExistentTab_doesNotThrow() {
        holder.destroyBeansForTab("nonexistent");
        // No exception means success
    }

    // ---------------------------------------------------------------
    // destroyAllBeansForSession
    // ---------------------------------------------------------------

    /**
     * destroyAllBeansForSession should remove all tabs and beans for the session,
     * calling destroy on each bean.
     */
    @SuppressWarnings("unchecked")
    @Test
    void destroyAllBeansForSession_removesAllBeansAndCallsDestroy() {
        Contextual<String> c1 = mock(Contextual.class);
        Contextual<String> c2 = mock(Contextual.class);
        CreationalContext<String> cc = mock(CreationalContext.class);
        holder.putBean("r0", c1, "bean1", cc);
        holder.putBean("r1", c2, "bean2", cc);

        holder.destroyAllBeansForSession("test-session");

        assertThat(holder.getBean("r0", c1)).isNull();
        assertThat(holder.getBean("r1", c2)).isNull();
        verify(c1).destroy("bean1", cc);
        verify(c2).destroy("bean2", cc);
    }

    /**
     * Destroying a non-existent session should not throw an exception.
     */
    @Test
    void destroyAllBeansForSession_nonExistentSession_doesNotThrow() {
        holder.destroyAllBeansForSession("no-such-session");
        // No exception means success
    }

    // ---------------------------------------------------------------
    // migrateSession
    // ---------------------------------------------------------------

    /**
     * After migrating a session, beans should be accessible under the new session ID
     * and no longer under the old one.
     */
    @SuppressWarnings("unchecked")
    @Test
    void migrateSession_movesBeansBetweenSessionIds() {
        Contextual<String> contextual = mock(Contextual.class);
        CreationalContext<String> cc = mock(CreationalContext.class);
        holder.putBean("r0", contextual, "instance", cc);

        TabScopedContextHolder.migrateSession("test-session", "new-session");

        // Switch FacesContext to return the new session ID
        when(externalContext.getSessionId(false)).thenReturn("new-session");

        assertThat(holder.getBean("r0", contextual).getInstance()).isEqualTo("instance");
    }

    /**
     * Migrating a session that has no beans should not throw.
     */
    @Test
    void migrateSession_nonExistentOldSession_doesNotThrow() {
        TabScopedContextHolder.migrateSession("no-such-session", "new-session");
        // No exception means success
    }

    // ---------------------------------------------------------------
    // getActiveSessionCount
    // ---------------------------------------------------------------

    /**
     * Active session count should reflect the number of sessions with stored beans.
     */
    @SuppressWarnings("unchecked")
    @Test
    void getActiveSessionCount_reflectsStoredSessions() {
        int before = TabScopedContextHolder.getActiveSessionCount();

        Contextual<String> contextual = mock(Contextual.class);
        CreationalContext<String> cc = mock(CreationalContext.class);
        holder.putBean("r0", contextual, "instance", cc);

        assertThat(TabScopedContextHolder.getActiveSessionCount()).isEqualTo(before + 1);
    }

    // ---------------------------------------------------------------
    // Session isolation
    // ---------------------------------------------------------------

    /**
     * Beans stored under one session should not be visible from another session.
     * This verifies cross-session isolation (user A cannot see user B's beans).
     */
    @SuppressWarnings("unchecked")
    @Test
    void sessions_areIsolated_beansNotSharedBetweenSessions() {
        Contextual<String> contextual = mock(Contextual.class);
        CreationalContext<String> cc = mock(CreationalContext.class);

        // Store bean in "test-session"
        holder.putBean("r0", contextual, "sessionA-bean", cc);

        // Switch to a different session
        when(externalContext.getSessionId(false)).thenReturn("other-session");

        // Should not see the bean from the first session
        assertThat(holder.getBean("r0", contextual)).isNull();
    }
}
