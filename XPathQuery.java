/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.cysecurity.cspf.jvl.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathVariableResolver;

import org.w3c.dom.Document;
/**
 *
 * @author breakthesec
 */
public class XPathQuery extends HttpServlet {

    private static final Pattern SAFE_CREDENTIAL_PATTERN = Pattern.compile("^[a-zA-Z0-9_@.\\-]{1,100}$");

            
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            String user=request.getParameter("username");
            String pass=request.getParameter("password");
            if (!isValidCredentialInput(user) || !isValidCredentialInput(pass)) {
                response.sendRedirect(response.encodeURL("ForwardMe?location=/vulnerability/Injection/xpath_login.jsp?err=Invalid Credentials"));
                return;
            }
            
            //XML Source:
            String XML_SOURCE=getServletContext().getRealPath("/WEB-INF/users.xml");
            
            //Parsing XML:
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder=factory.newDocumentBuilder();
            Document xDoc=builder.parse(XML_SOURCE);
            
            XPath xPath=XPathFactory.newInstance().newXPath();

            final String safeUser = user;
            final String safePass = pass;
            xPath.setXPathVariableResolver(new XPathVariableResolver() {
                @Override
                public Object resolveVariable(QName varName) {
                    if ("username".equals(varName.getLocalPart())) {
                        return safeUser;
                    }
                    if ("password".equals(varName.getLocalPart())) {
                        return safePass;
                    }
                    return null;
                }
            });

            // XPath query with bound variables avoids query-structure injection.
            XPathExpression expression = xPath.compile("/users/user[username=$username and password=$password]/name");
            String name=expression.evaluate(xDoc);
            out.println(name);
            if(name.isEmpty())
            {
                response.sendRedirect(response.encodeURL("ForwardMe?location=/vulnerability/Injection/xpath_login.jsp?err=Invalid Credentials"));
            }
            else
            {
                 HttpSession session=request.getSession();
                 session.setAttribute("isLoggedIn", "1");
                  session.setAttribute("user", name);
                 response.sendRedirect(response.encodeURL("ForwardMe?location=/index.jsp"));                                  
            }
        } 
        catch(Exception e)
        {
            out.print(e);
        }        
        finally {
            out.close();
        }
    }

    private boolean isValidCredentialInput(String value) {
        return value != null && SAFE_CREDENTIAL_PATTERN.matcher(value).matches();
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
