package dyntabs;

/**
 * Event object representing a tab lifecycle event in the DynTabs framework.
 *
 * <p>Supported event types:</p>
 * <ul>
 *   <li>{@code "dynTabAdded"} - fired when a new tab is opened</li>
 *   <li>{@code "dynTabRemoved"} - fired when a tab is closed</li>
 *   <li>{@code "dynTabSelected"} - fired when a tab is selected (brought to front)</li>
 * </ul>
 *
 * <p>Events are dispatched by {@link DynTabManager#fireDynTabEvent} to all active tabs.
 * Each tab's CDI bean receives the event via
 * {@link BaseDyntabCdiBean#observeDynTabEvent(DynTabCDIEvent)}.</p>
 *
 * <p>Note: For {@code "dynTabRemoved"} events, the {@link #tab} reference is {@code null}
 * because the bean has already been destroyed before the event is dispatched.
 * Use {@link #getTabId()}, {@link #getUniqueIdentifier()}, and {@link #getTitle()}
 * to access tab metadata in this case.</p>
 *
 * @author DynTabs
 * @see DynTabManager
 * @see BaseDyntabCdiBean#observeDynTabEvent(DynTabCDIEvent)
 */
public class DynTabCDIEvent {
   // Possible values: dynTabAdded, dynTabRemoved, dynTabSelected
   private String eventType;
   private DynTab tab;

   // Tab metadata - always available, even when the tab reference is null
   // (e.g. for "dynTabRemoved" where the bean has already been destroyed)
   private String tabId;
   private String uniqueIdentifier;
   private String title;

   /**
    * Constructor for events where the tab reference still exists
    * (dynTabAdded, dynTabSelected).
    *
    * @param eventType the type of event
    * @param tab       the DynTab instance involved in the event
    */
   public DynTabCDIEvent(String eventType, DynTab tab) {
      this.eventType = eventType;
      this.tab = tab;
      if (tab != null) {
         this.tabId = tab.getId();
         this.uniqueIdentifier = tab.getUniqueIdentifier();
         this.title = tab.getTitle();
      }
   }

   /**
    * Constructor for events where the tab reference no longer exists
    * (dynTabRemoved - the bean is destroyed before the event is dispatched).
    * Only the tab metadata is carried.
    *
    * @param eventType        the type of event
    * @param tabId            the tab's internal ID (e.g. "r0")
    * @param uniqueIdentifier the tab's unique identifier
    * @param title            the tab's display title
    */
   public DynTabCDIEvent(String eventType, String tabId, String uniqueIdentifier, String title) {
      this.eventType = eventType;
      this.tab = null;
      this.tabId = tabId;
      this.uniqueIdentifier = uniqueIdentifier;
      this.title = title;
   }

   public String getEventType() {
      return eventType;
   }

   public void setEventType(String eventType) {
      this.eventType = eventType;
   }

   public DynTab getTab() {
      return tab;
   }

   public void setTab(DynTab tab) {
      this.tab = tab;
   }

   public String getTabId() {
      return tabId;
   }

   public String getUniqueIdentifier() {
      return uniqueIdentifier;
   }

   public String getTitle() {
      return title;
   }
}
