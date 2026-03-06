package dyntabs;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import jakarta.el.ValueExpression;
import jakarta.faces.FactoryFinder;
import jakarta.faces.application.Application;
import jakarta.faces.application.ApplicationFactory;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UINamingContainer;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletRequest;

import org.primefaces.PrimeFaces;
import org.primefaces.component.dialog.Dialog;
import org.primefaces.model.file.UploadedFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static utility class providing common JSF and PrimeFaces helper methods.
 *
 * <p>Includes utilities for:</p>
 * <ul>
 *   <li>EL expression evaluation ({@link #getExpressionValue})</li>
 *   <li>Component tree traversal ({@link #findComponent}, {@link #findComponentInDynamicTab})</li>
 *   <li>Parent component lookup ({@link #findParentNamingContainer}, {@link #findParentForm})</li>
 *   <li>PrimeFaces dialog management ({@link #openPFDialog})</li>
 *   <li>Resource bundle access ({@link #getStringFromAppResourceBundle})</li>
 *   <li>File download ({@link #downloadFile}) and upload ({@link #readFileBytes})</li>
 * </ul>
 *
 * @author DynTabs
 */
public class JsfUtils {

   private static final Logger log = LoggerFactory.getLogger(JsfUtils.class);

   public static void refreshComponentInDynamicTab(String compId, String dynTabId) {
      UIComponent comp = JsfUtils.findComponentInDynamicTab(compId, dynTabId);
      if (comp != null) {
         PrimeFaces.current().ajax().update(comp.getClientId());
      } else {
         log.warn("refreshComponentInDynamicTab(): component with id {} does not exist in dynamic tab {}", compId, dynTabId);
      }
   }

   public static Object getExpressionValue(String jsfExpression) {
      // when specifying EL expression in managed bean as "literal" value
      // so t can be evaluated later, the # is replaced with $, quite strange
      if (jsfExpression == null) {
         return jsfExpression;
      }
      if (jsfExpression.startsWith("${")) {
         jsfExpression = "#{" + jsfExpression.substring(2);
      }
      if (!jsfExpression.startsWith("#{")) {
         if (jsfExpression.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
         } else if (jsfExpression.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
         }
         // there can be literal text preceding the expression...
         else if (jsfExpression.indexOf("#{") < 0) {
            return jsfExpression;
         }
      }
      ValueExpression ve = getApplication().getExpressionFactory()
               .createValueExpression(FacesContext.getCurrentInstance().getELContext(), jsfExpression, Object.class);
      return ve.getValue(FacesContext.getCurrentInstance().getELContext());
   }

   private static String APPLICATION_FACTORY_KEY = "jakarta.faces.application.ApplicationFactory";

   public static Application getApplication() {
      FacesContext context = FacesContext.getCurrentInstance();
      if (context != null) {
         return FacesContext.getCurrentInstance().getApplication();
      } else {
         ApplicationFactory afactory = (ApplicationFactory) FactoryFinder
                  .getFactory(APPLICATION_FACTORY_KEY);
         return afactory.getApplication();
      }
   }

   public static UINamingContainer findParentNamingContainer(UIComponent component) {
      if (component == null) {
         return null;
      }

      UIComponent parent = component.getParent();
      UINamingContainer nc = null;
      while (parent != null) {
         if (parent instanceof UINamingContainer) {
            nc = (UINamingContainer) parent;
            break;
         }
         parent = parent.getParent();
      }

      return nc;
   }

   /**
    * Finds the first parent NamingContainer of the given component whose ID starts with the given prefix.
    *
    * @param component the starting component
    * @param idPrefix  the ID prefix to match
    * @return the matching parent NamingContainer, or null if not found
    */
   public static UINamingContainer findParentNamingContainerWithIdPrefix(UIComponent component, String idPrefix) {
      log.debug("findParentNamingContainerWithIdPrefix() begin");
      if (component == null) {
         return null;
      }

      UIComponent parent = component.getParent();
      UINamingContainer nc = null;
      while (parent != null) {
         if (parent instanceof UINamingContainer) {
            nc = (UINamingContainer) parent;
            String ncId = parent.getId();
            log.debug("-ncId = {}", ncId);
            if (ncId.startsWith(idPrefix)) {
               break;
            }
         }
         parent = parent.getParent();
      }
      log.debug("findParentNamingContainerWithIdPrefix() returns: {}", nc);
      return nc;
   }

   public static UIComponent findParentWithIdPrefix(UIComponent component, String idPrefix) {
      if (component == null) {
         return null;
      }

      UIComponent parent = component.getParent();
      while (parent != null) {
         String Id = parent.getId();
         if (Id.startsWith(idPrefix)) {
            break;
         }
         parent = parent.getParent();
      }
      if ((parent != null) && !(parent instanceof org.primefaces.component.dialog.Dialog)) {
         log.warn("findParentWithIdPrefix() WARNING: for idPrefix {} found parent that is NOT a p:dialog: {}",
                  idPrefix, parent.getClass().getName());
      }
      return parent;

   }

   public static UIComponent findParentForm(UIComponent component) {
      if (component == null) {
         return null;
      }

      UIComponent parent = component.getParent();
      while (parent != null) {
         String Id = parent.getId();
         if (parent instanceof jakarta.faces.component.html.HtmlForm) {
            break;
         }
         parent = parent.getParent();
      }
      return parent;

   }

   public static UIComponent findParentComponentOfClass(UIComponent component, Class clazz) {

      if (component == null) {
         return null;
      }
      UIComponent parent = component.getParent();
      while (parent != null) {
         if (clazz.isInstance(parent)) {
            break;
         }
         parent = parent.getParent();
      }
      return parent;
   }

   public static UIComponent findComponent(UIComponent base, String id) {

      // Is the "base" component itself the match we are looking for?
      if (id.equals(base.getId())) {
         return base;
      }
      // check for direct child
      UIComponent result = base.findComponent(id);
      if (result != null) {
         return result;
      }

      // Search through our facets and children
      UIComponent kid = null;
      Iterator kids = base.getFacetsAndChildren();
      while (kids.hasNext() && (result == null)) {
         kid = (UIComponent) kids.next();
         if (id.equals(kid.getId())) {
            result = kid;
            break;
         }
         result = findComponent(kid, id);
         if (result != null) {
            break;
         }
      }
      return result;
   }// of findComponent()

   public static Collection<String> findComponentsClientId(UIComponent base, String id) {
      log.debug("-checking {}", base.getClientId());
      ArrayList<String> result = new ArrayList<String>();
      if (base.getId().endsWith(id)) {
         result.add(base.getClientId());
      }

      // Search through our facets and children
      UIComponent kid = null;
      Iterator kids = base.getFacetsAndChildren();
      while (kids.hasNext()) {
         kid = (UIComponent) kids.next();
         result.addAll(findComponentsClientId(kid, id));
      }
      return result;
   }// of findComponentsClientId()

   public static UIComponent findComponentInDynamicTab(String componentId, String dynTabId) {
      log.debug("findComponentInDynamicTab() begin, componentId = {}, dynTabId = {}", componentId, dynTabId);
      UIComponent result = null;
      // mainForm:mainTabView:_sub_r1
      // mainForm:mainTabView:_sub_r1:fragmentPanel
      // Object dynTabObj = getExpressionValue("#{dynTab}"); // salje se fragmentu kao ui:param u masterLayout.xhtml - template za
      // main oage
      String fragmentPanelClientId = "mainForm:mainTabView:_sub_" + dynTabId + ":fragmentPanel";
      UIComponent fragmentPanel = findComponentByClientId(fragmentPanelClientId);
      log.debug("fragmentPanel = {}", fragmentPanel);
      if (fragmentPanel != null) {
         log.debug("fragmentPanel clientId = {}", fragmentPanel.getClientId());
         result = findComponent(fragmentPanel, componentId);
      }
      log.debug("findComponentInDynamicTab() returns: {}", result);
      return result;
   }

   /**
    * Within the first parent NamingContainer of the given component (whose ID starts with
    * {@code ncIdPrefix}), finds and returns the child component with the specified ID.
    *
    * @param component  the starting component
    * @param ncIdPrefix the ID prefix for the parent NamingContainer
    * @param Id         the ID of the component to find
    * @return the found component, or null if not found
    */
   public static UIComponent findComponentWithIdInNamigContainer(UIComponent component, String ncIdPrefix, String Id) {
      log.debug("findComponentWithIdInNamigContainer() begin, ncIdPrefix = {}, Id = {}", ncIdPrefix, Id);
      // For the given component, find the first parent NamingContainer whose id starts with ncIdPrefix:
      UINamingContainer nc = findParentNamingContainerWithIdPrefix(component, ncIdPrefix);
      // ===
      if (nc == null) {
         return null;
      }
      UIComponent result = findComponent(nc, Id);
      log.debug("findComponentWithIdInNamigContainer() returns: {}", result);
      return result;
   }

   public static ValueExpression createValueExpression(String exprStr, Class cls) {
      FacesContext context = FacesContext.getCurrentInstance();
      Application app = context.getApplication();
      ValueExpression valueEx = app.getExpressionFactory().createValueExpression(context.getELContext(), exprStr, cls);
      log.debug("for exprStr = {}, createValueExpression() returns: {}", exprStr, valueEx);
      return valueEx;
   }

   /**
    * Called in DynTabManager when closing a p:tab.
    * Closes the dialog and sets its visible condition to false.
    * This is the only way to close a dialog from Java code, BUT it appears
    * it does NOT trigger the listener for p:ajax event="close" on the dialog,
    * so when closing a dialog from Java, visibility must be managed in the Java code itself.
    *
    * NOT WORKING
    *
    * public static void closePFDialog(Dialog dlg) {
    * String jsCloseDlg = "PF('" + dlg.resolveWidgetVar() + "').hide()";
    *
    * PrimeFaces.current().executeScript(jsCloseDlg);
    * System.out.println("closePFDialog(), for dialog " + dlg.getClientId() + " called " + jsCloseDlg);
    * removePFDialogVisibleCondition(dlg.getClientId());
    * }
    */

   /**
    * Opens a PrimeFaces dialog by setting its visibility condition in the viewScope.
    *
    * <p>Each CRUD dialog has a visible condition expression:
    * {@code "#{viewScope." + dlg.getClientId().replace(":", "_") + "}"}
    * which is set up in {@code solveDlgVisible()}.
    * This method makes that condition evaluate to true, making the dialog visible.</p>
    *
    * <p>NOT WORKING</p>
    *
    * @param dlg the PrimeFaces dialog to open
    */
   public static void openPFDialog(Dialog dlg) {
      String dlgClientId = dlg.getClientId();
      Map<String, Object> viewMap = FacesContext.getCurrentInstance().getViewRoot().getViewMap();
      String dlgClId_rep = dlgClientId.replace(":", "_");
      viewMap.put(dlgClId_rep, true);
      log.debug("-DIALOG OPENED {}, viewAttr: {}", dlgClientId, dlgClId_rep);
      PrimeFaces.current().ajax().update(dlgClientId);

   }

   /**
    * Removes the visibility attribute for a p:dialog from the viewScope.
    *
    * <p>Each CRUD dialog has a visible expression set up via
    * {@code f:event listener="#{drugiBean.solveDlgVisible}" type="postAddToView"},
    * specifically in {@code adjustVisibleForDialog()}, in the form:
    * {@code "#{viewScope." + dlg.getClientId().replace(":", "_") + "}"}</p>
    *
    * <p>To make the dialog visible, {@code crudListener()} sets this viewScope attribute to true.
    * To hide the dialog, this method removes the attribute so the expression evaluates to false.</p>
    *
    * <p>NOT WORKING</p>
    *
    * @param dlgClientId
    *
    *
    *                    public static void removePFDialogVisibleCondition(String dlgClientId) {
    *                    Map<String, Object> viewMap = FacesContext.getCurrentInstance().getViewRoot().getViewMap();
    *                    String dlgClId_rep = dlgClientId.replace(":", "_");
    *                    viewMap.remove(dlgClId_rep);
    *                    System.out.println(" -removed viewScope attribute: " + dlgClientId.replace(":", "_"));
    *                    System.out.println("_DIALOG CLOSED " + dlgClientId + ", REMOVED viewAttr: " + dlgClId_rep);
    *                    }
    */
   public static UIComponent findComponentByClientId(String clientId) {
      UIViewRoot view = FacesContext.getCurrentInstance().getViewRoot();
      UIComponent result = view.findComponent(clientId);
      return result;
   }

   public static String DEFAULT_ERROR_MSG = "Error!";

   public static String getStringFromAppResourceBundle(String rbName, String key) {
      ResourceBundle bundle = getAppResourceBundle(rbName);
      return getStringSafely(bundle, key, key);
   }

   public static ResourceBundle getAppResourceBundle(String rbName) {
      FacesContext fc = FacesContext.getCurrentInstance();
      ResourceBundle rb = fc.getApplication().getResourceBundle(fc, rbName);
      if (rb == null) {
         log.warn("Resource Bundle does not exist: {}", rbName);
      }
      return rb;
   }

   public static final String NO_RESOURCE_FOUND = "Resource not found: ";

   private static String getStringSafely(ResourceBundle bundle, String key,
            String defaultValue) {

      if (bundle == null) {
         return defaultValue;
      }
      String resource = null;
      try {
         resource = bundle.getString(key);
      } catch (MissingResourceException mrex) {
         log.warn("MissingResourceException: {}", mrex.getKey());
         if (defaultValue != null) {
            resource = defaultValue;
         } else {
            resource = NO_RESOURCE_FOUND + key;
         }
      }
      return resource;
   }

   public static void downloadFile(byte[] fileBytes, String mimeType, String fileName) throws IOException {
      FacesContext fc = FacesContext.getCurrentInstance();
      ExternalContext ec = fc.getExternalContext();
      ec.responseReset();
      ec.setResponseContentType(mimeType);
      ec.setResponseContentLength(fileBytes.length);
      ec.setResponseHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
      OutputStream output = ec.getResponseOutputStream();
      output.write(fileBytes);
      fc.responseComplete();
   }

   public static byte[] readFileBytes(UploadedFile file) {
      InputStream is = null;
      ByteArrayOutputStream baos = null;

      try {
         is = file.getInputStream();
         baos = new ByteArrayOutputStream();
         byte buffer[] = new byte[2048];
         int len;
         while ((len = is.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
         }
         baos.flush();
         return baos.toByteArray();
      } catch (Exception exc) {
         throw new RuntimeException(exc.getMessage());
      } finally {
         if (is != null) {
            try {
               is.close();
            } catch (Exception ex) {
               is = null;
            }
         }
         if (baos != null) {
            try {
               baos.close();
            } catch (Exception ex) {
               baos = null;
            }
         }
      }
   }

   /**
    * @return
    */
   public static String getURLBase() {
      StringBuffer retVal = new StringBuffer();
      HttpServletRequest req =  ((HttpServletRequest)FacesContext.getCurrentInstance().getExternalContext().getRequest());
      try{
         URL url = new URL(req.getRequestURL().toString());

         retVal.append(url.getProtocol());// Protocol (was previously hardcoded due to security issues)
         retVal.append("://");
         String host = url.getHost();

         if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equalsIgnoreCase(host)) {
            host = "192.168.67.230";
         }
         retVal.append(host); // needed for external internet access
         retVal.append(":");
         int port = url.getPort();
         retVal.append(port);//
         retVal.append("/");//
         //retVal.append(req.getContextPath());
         // retVal.append("/");

      }catch( MalformedURLException ex){
         log.error("Error retrieving base URL: {}", ex.getMessage(), ex);
      }
      log.debug("getUrlBase() returns: {}", retVal.toString());
      return retVal.toString();
   }

}
