package dyntabs;

/**
 * Event object used for inter-tab (application module) communication.
 *
 * <p>Tabs in DynTabs can send messages to each other using this event.
 * Each tab is identified by its {@code uniqueIdentifier}, which serves as
 * the sender/target address. The event carries an optional payload object
 * that can be any application-specific data.</p>
 *
 * <p>Events are dispatched by {@link DynTabManager#fireApplicationEvent(ApplicationCDIEvent)}
 * which iterates through all active tabs and delivers the event directly
 * (bypassing CDI {@code @Observes} due to {@code @TabScoped} limitations).</p>
 *
 * <p>Special event types:</p>
 * <ul>
 *   <li>{@code "JobFlowReturn"} - signals that a child tab is returning a value
 *       to its caller tab (workflow completion pattern)</li>
 * </ul>
 *
 * @author DynTabs
 * @see DynTabManager#fireApplicationEvent(ApplicationCDIEvent)
 * @see BaseDyntabCdiBean#observeApplicationEvent(ApplicationCDIEvent)
 */
public class ApplicationCDIEvent {
   private String senderAppModuleId;
   private String targetAppModuleId;
   private Object payload;

   /**
    * Creates an event without an explicit event type.
    *
    * @param senderAppModuleId uniqueIdentifier of the tab that fires the event
    * @param targetAppModuleId uniqueIdentifier of the tab that should receive the event
    *                          (null to broadcast to all tabs)
    * @param payload           arbitrary data object to send with the event
    */
   public ApplicationCDIEvent(String senderAppModuleId, String targetAppModuleId, Object payload) {
      this.senderAppModuleId = senderAppModuleId;
      this.targetAppModuleId = targetAppModuleId;
      this.payload = payload;
   }

   private String eventType = null;
   /**
    * Creates an event with an explicit event type.
    *
    * @param eventType         the type of event (e.g. "JobFlowReturn")
    * @param senderAppModuleId uniqueIdentifier of the tab that fires the event
    * @param targetAppModuleId uniqueIdentifier of the tab that should receive the event
    *                          (null to broadcast to all tabs)
    * @param payload           arbitrary data object to send with the event
    */
   public ApplicationCDIEvent(String eventType, String senderAppModuleId, String targetAppModuleId, Object payload) {
      this(senderAppModuleId, targetAppModuleId, payload);
      this.eventType = eventType;
   }


   public String getSenderAppModuleId() {
      return this.senderAppModuleId;
   }


   public String getTargetAppModuleId() {
      return this.targetAppModuleId;
   }
   public Object getPayload() {
      return this.payload;
   }

   public String getEventType() {
      return this.eventType;
   }

}