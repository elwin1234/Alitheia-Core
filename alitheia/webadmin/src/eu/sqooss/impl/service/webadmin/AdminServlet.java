/*
 * This file is part of the Alitheia system, developed by the SQO-OSS
 * consortium as part of the IST FP6 SQO-OSS project, number 033331.
 *
 * Copyright 2007 by the SQO-OSS consortium members <info@sqo-oss.eu>
 * Copyright 2007 by Adriaan de Groot <groot@kde.org>
 *
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package eu.sqooss.impl.service.webadmin;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import eu.sqooss.service.db.DBService;
import eu.sqooss.service.logging.LogManager;
import eu.sqooss.service.logging.Logger;
import eu.sqooss.service.tds.TDSService;
import eu.sqooss.util.SQOUtils;

import java.util.Hashtable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;

// Java Extensions
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;

public class AdminServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private BundleContext bundlecontext = null;

    private LogManager logService = null;
    private Logger logger = null;
    private DBService dbService = null;
    private TDSService tdsService = null;

    private Hashtable<String,String[]> staticContentMap;
    private Hashtable<String,String> dynamicContentMap;
    private Hashtable<String,String> dynamicSubstitutions;

    private void getLogger(BundleContext bc) {
        ServiceReference serviceRef = null;
        serviceRef = bc.getServiceReference(LogManager.class.getName());
        logService = (LogManager) bc.getService(serviceRef);

        if (logService != null) {
            logger = logService.createLogger(Logger.NAME_SQOOSS_WEBUI);

            if (logger != null) {
                logger.info("WebAdmin got logger.");
            }
        } else {
            System.out.println("! Got neither a service nor a logger.");
        }
    }

    private void getDB(BundleContext bc) {
        ServiceReference serviceRef = bc.getServiceReference(DBService.class.getName());
        if (serviceRef != null) {
            dbService = (DBService) bc.getService(serviceRef);
            if (logger != null) {
                logger.info("Got DB service.");
            }
        } else {
            if (logger != null) {
                logger.warning("Did not get DB service.");
            } else {
                System.out.println("! Got no DB service.");
            }
        }
    }

    private void getTDS(BundleContext bc) {
        ServiceReference serviceRef = bc.getServiceReference(TDSService.class.getName());
        if (serviceRef != null) {
            tdsService = (TDSService) bc.getService(serviceRef);
            if (logger != null) {
                logger.info("Got TDS service.");
            }
        } else {
            if (logger != null) {
                logger.warning("Did not get TDS service.");
            } else {
                System.out.println("! Got no TDS service.");
            }
        }
    }

    public AdminServlet(BundleContext bc) {
        bundlecontext = bc;
        getLogger(bc);
        getDB(bc);
        getTDS(bc);
        Stuffer myStuffer = new Stuffer(dbService, logger, tdsService);
        Thread t = new Thread(myStuffer, "DB to TDS Stuffer");
        t.start();

        staticContentMap = new Hashtable<String,String[]>();
        String[] flossie = { "image/x-png", "/flossie.png" } ;
        String[] css = { "text/css", "/alitheia.css" } ;
        String[] logo = { "image/x-png", "/alitheia.png" } ;
        staticContentMap.put("logo", flossie);
        staticContentMap.put("alitheia.css", css);
        staticContentMap.put("alitheia.png", logo);

        dynamicContentMap = new Hashtable<String,String>();
        dynamicContentMap.put("about","/about.html");
        dynamicContentMap.put("status","/index.html");
        dynamicContentMap.put("index","/index.html");
        dynamicContentMap.put("addproject","/addproject.html");
        dynamicContentMap.put("list","/listproject.html");
        dynamicContentMap.put("logs","/logs.html");

        dynamicSubstitutions = new Hashtable<String,String>();
    }

    protected String[] getServiceNames(ServiceReference[] servicerefs) {
	if( servicerefs != null ){
	    String s, names[] = new String[servicerefs.length];
	    int i = 0;

	    for (ServiceReference r : servicerefs) {
		Object clazz = 
		    r.getProperty( org.osgi.framework.Constants.OBJECTCLASS );
		
		if (clazz != null) {
		    s = SQOUtils.join( (String[])clazz, ", ");
		} else {
		    s = "No class defined";
		}
        
		names[i++] = s;
	    }
        
	    return names;
	} else {
	    return null;
	}
    }

    /**
     * Creates an HTML table displaying the key information on
     * bundles and the services that they supply
    */
    protected String renderBundles() {
        if( bundlecontext != null ) {
            Bundle[] bundles = bundlecontext.getBundles();
            String[] statenames = { 
                "uninstalled",
                "installed",
                "resolved",
                "starting",
                "stopping",
                "active" 
            };

	    StringBuilder resultString = new StringBuilder();
 	    resultString.append("<table>\n");
	    resultString.append("\t<thead>\n");
	    resultString.append("\t\t<tr>\n");
	    resultString.append("\t\t\t<th>Bundle Name</th>\n");
	    resultString.append("\t\t\t<th>Status</th>\n");
	    resultString.append("\t\t\t<th>Services Utilised</th>\n");
	    resultString.append("\t\t</tr>\n");
	    resultString.append("\t</thead>\n");
	    resultString.append("\t<tbody>\n");
	    
	    for( Bundle b : bundles ){
		String[] names = getServiceNames(b.getRegisteredServices());

		resultString.append("\t\t<tr>\n\t\t\t<th>");
		resultString.append(SQOUtils.makeXHTMLSafe(b.getSymbolicName()));
		resultString.append("</th>\n\t\t\t<th>"); 
		resultString.append(SQOUtils.bitfieldToString(statenames,b.getState()));
		resultString.append("</th>\n\t\t\t<th>\n\t\t\t\t<ul>\n"); 
		resultString.append(renderList(names));
		resultString.append("\t\t\t\t</ul>\n\t\t\t</th>\n\t\t</tr>\n");
	    }
	    
	    resultString.append("\t</tbody>\n");
	    resultString.append("</table>");
	    
            return resultString.toString();
        } else {
            return null;
        }
    }           

    public String renderList(String[] names) {
        if ((names != null) && (names.length > 0)) {
            StringBuilder b = new StringBuilder();
            for (String s : names) {
                b.append("\t\t\t\t\t<li>" + SQOUtils.makeXHTMLSafe(s) + "</li>\n");
            }
            
            return b.toString();
        } else {
            return "\t\t\t\t\t<li>&lt;none&gt;</li>\n";
        }
    }

    /**
    * Sends a resource (stored in the jar file) as a response. The mime-type
    * is set to @p mimeType . The @p path to the resource should start
    * with a / .
    *
    * Test cases:
    *   - null mimetype, null path, bad path, relative path, path not found,
    *   - null response
    *
    * TODO: How to simulate conditions that will cause IOException
    */
    protected void sendResource(HttpServletResponse response, String mimeType, String path)
        throws ServletException, IOException {
        InputStream istream = getClass().getResourceAsStream(path);
        if ( istream == null ) {
            // TODO: Is there a more specific exception?
            throw new IOException( "Path not found: " + path );
        }

        byte[] buffer = new byte[1024];
        int bytesRead = 0;
        int totalBytes = 0;

        if ( logger != null ) {
            logger.info("Serving " + path + " (" + mimeType + ")");
        }

        response.setContentType(mimeType);
        ServletOutputStream ostream = response.getOutputStream();
        while ( (bytesRead = istream.read(buffer)) > 0 ) {
            ostream.write(buffer,0,bytesRead);
            totalBytes += bytesRead;
        }

        // TODO: Check that the bytes written were as many as the
        //  file size in the JAR (how? it's an InputStream).
        if ( logger != null ) {
            logger.info("Wrote " + totalBytes + " from " + path);
        }
    }

    protected void sendTemplate(HttpServletResponse response, String path, Hashtable<String,String> subs)
        throws ServletException, IOException {
        BufferedReader istream = new BufferedReader(
            new InputStreamReader(getClass().getResourceAsStream(path)));
        if ( istream == null ) {
            // TODO: Is there a more specific exception?
            throw new IOException( "Path not found: " + path );
        }

        if ( logger != null ) {
            logger.info("Serving template " + path);
        }

        response.setContentType("text/html");
        PrintWriter print = response.getWriter();
        while ( istream.ready() ) {
            String line = istream.readLine().trim();
            if ( line.startsWith("@@") && subs.containsKey(line) ) {
                print.println(subs.get(line));
            } else {
                print.println(line);
            }
        }
    }

    private void resetSubstitutions() {
        dynamicSubstitutions.clear();
        dynamicSubstitutions.put("@@STATUS","The cruncher is offline.");
        dynamicSubstitutions.put("@@LOGO","<img src='/logo' id='logo' alt='Logo' />");
        dynamicSubstitutions.put("@@COPYRIGHT","Copyright 2007 <a href=\"about\">SQO-OSS Consortium members</a>");
        dynamicSubstitutions.put("@@GETLOGS", renderList(logService.getRecentEntries()));
    }

    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException,
                                                              IOException {
        if ( logger != null ) {
            logger.info("GET path=" + request.getPathInfo());
        }

        String query = request.getPathInfo();
        if ( (query != null) && (staticContentMap.containsKey(query)) ) {
            String[] mapvalues = staticContentMap.get(query);
            sendResource(response, mapvalues[0], mapvalues[1]);
        } else {
            resetSubstitutions();
            dynamicSubstitutions.put("@@ABOUT","<p class='box'>This is the administrative interface.</p>");
            if ("list".equals(query)) {
                dynamicSubstitutions.put("@@PROJECTS",renderList(dbService.listProjects()));
            } else {
                dynamicSubstitutions.put("@@BUNDLE", renderBundles());
            }
            if ( (query != null) && dynamicContentMap.containsKey(query) ) {
                sendTemplate(response,dynamicContentMap.get(query),dynamicSubstitutions);
            } else {
                sendTemplate(response,"/index.html",dynamicSubstitutions);
            }
        }
    }

    private void addProject(HttpServletRequest request) {
        resetSubstitutions();

        String name = request.getParameter("name");
        String website = request.getParameter("website");
        String contact = request.getParameter("contact");
        String bts = request.getParameter("bts");
        String mail = request.getParameter("mail");
        String scm = request.getParameter("scm");

        if ( (name == null) ||
            (website == null) ||
            (contact == null) ||
            (bts == null) ||
            (mail == null) ||
            (scm == null) ) {
            dynamicSubstitutions.put("@@RESULTS","<p>Add project failed because some of the required information was missing.</p>");
            return;
        }

        if (dbService != null) {
            dbService.addProject(name,website,contact,bts,mail,scm);
        }
        logger.info("Added a new project.");
        dynamicSubstitutions.put("@@RESULTS","<p>New project added successfully.</p>");
    }

    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws ServletException,
                                                               IOException {
        logger.info("POST path=" + request.getPathInfo());
        if ("addproject".equals(request.getPathInfo())) {
            addProject(request);
            // addProject() has filled in the substitutions by now
            sendTemplate(response,"/results.html",dynamicSubstitutions);
        } else {
            doGet(request,response);
        }
    }

}


// vi: ai nosi sw=4 ts=4 expandtab

