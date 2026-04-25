package org.example.domaci2;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.servlet.annotation.*;

@WebServlet(name = "helloServlet", urlPatterns =  {"/", "/order", "/reset", "/admin"})
public class HelloServlet extends HttpServlet {
    private final List<String> days = Arrays.asList("ponedeljak", "utorak", "sreda", "cetvrtak", "petak");

    private Map<String, List<String>> menu;
    private Map<String, Map<String,Integer>> orders;
    private String password;
    private int counter=1;

    @Override
    public void init() throws ServletException {
        menu = new LinkedHashMap<>();
        orders = new LinkedHashMap<>();

        password = readFile("/WEB-INF/password.txt").get(0).trim();
        
        for(String day: days){
            List<String> meals=readFile("/WEB-INF/"+day+".txt");
            menu.put(day,meals);
            
            Map<String,Integer> amts = new LinkedHashMap<>();
            for(String meal:meals){
                amts.put(meal,0);
            }
            
            orders.put(day,amts);
        }
    }

    private List<String> readFile(String filePath){
        List<String> lines = new ArrayList<>();

        InputStream inputStream=getServletContext().getResourceAsStream(filePath);

        if(inputStream!=null){
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String line;
                while((line=br.readLine())!=null){
                    if(!line.trim().isEmpty()){
                        lines.add(line.trim());
                    }
                }
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        return lines;

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        resp.setContentType("text/html; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getServletPath();
        if(path.equals("/admin"))
            showAdmin(req,resp);
        else
            showUser(req,resp);

    }

    private void showUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PrintWriter out = response.getWriter();
        HttpSession session = request.getSession();

        out.println("<html><head> <meta charset=\"utf-8\"><title>Choose your food</title></head><body>");
        out.println("<h1>Choose your food</h1>");

        Integer userCount = (Integer) session.getAttribute("counter");

        if (userCount != null && userCount == counter) {
            out.println("<h2>Vasa porudzbina je uspesno zabelezena</h2>");
            out.println("<h3>Vaaa odabrana jela:</h3>");


            Map<String, String> izabrano = (Map<String, String>) session.getAttribute("chosenMeals");
            for (String dan : days) {
                out.println("<p><b>" + kapitalizuj(dan) + ":</b> " + izabrano.get(dan) + "</p>");
            }
            out.println("</body></html>");
            return;
        }

        out.println("<h2>Odaberite vas rucak:</h2>");
        out.println("<form method='POST' action='" + request.getContextPath() + "/order'>");

        for (String dan : days) {
            out.println("<label><b>" + kapitalizuj(dan) + "</b></label><br>");
            out.println("<select name='" + dan + "' required>");
            out.println("<option value='' disabled selected>Izaberite jelo...</option>");
            for (String jelo : menu.get(dan)) {
                out.println("<option value='" + jelo + "'>" + jelo + "</option>");
            }
            out.println("</select><br>");
        }

        out.println("<button type='submit'>Potvrdite unos</button>");
        out.println("</form></body></html>");
    }

    private void showAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String unesenaLozinka = request.getParameter("lozinka");

        if (unesenaLozinka == null || !unesenaLozinka.equals(password)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().println("<html><body><h1>Pristup odbijen. Neispravna lozinka.</h1></body></html>");
            return;
        }

        PrintWriter out = response.getWriter();
        out.println("<html><head><meta charset=\"utf-8\"><title>Odabrana jela</title></head><body>");
        out.println("<h1>Odabrana jela</h1>");

        out.println("<form method='POST' action='" + request.getContextPath() + "/reset'>");
        out.println("<input type='hidden' name='lozinka' value='" + password + "'>");
        out.println("<button type='submit'>Očisti</button>");
        out.println("</form>");

        out.println("<table border='1' style='border-collapse: collapse; width: 50%; margin-top: 20px;'>");
        out.println("<tr><th>Dan</th><th>#</th><th>Jelo</th><th>Količina</th></tr>");

        for (String dan : days) {
            Map<String, Integer> jelaZaDan = orders.get(dan);
            int redniBroj = 1;
            for (Map.Entry<String, Integer> entry : jelaZaDan.entrySet()) {
                out.println("<tr>");
                if (redniBroj == 1) {
                    out.println("<td rowspan='" + jelaZaDan.size() + "'><b>" + kapitalizuj(dan) + "</b></td>");
                }
                out.println("<td>" + redniBroj++ + "</td>");
                out.println("<td>" + entry.getKey() + "</td>");
                out.println("<td>" + entry.getValue() + "</td>");
                out.println("</tr>");
            }
        }

        out.println("</table></body></html>");
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();

        if (path.equals("/order"))
            processOrder(req, resp);
        else if (path.equals("/reset"))
            deleteOrders(req, resp);

    }



    private void processOrder(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        HttpSession session=req.getSession();
        Map<String, String> choice= new HashMap<>();

        synchronized (this){

            for(String day: days){
                String chosenMeal = req.getParameter(day);
                if(chosenMeal!=null){
                    choice.put(day,chosenMeal);

                    Map<String,Integer> amts = orders.get(day);
                    amts.put(chosenMeal,amts.getOrDefault(chosenMeal,0)+1);

                }

            }

        }

        session.setAttribute("counter",counter);
        session.setAttribute("chosenMeals", choice);

        resp.sendRedirect(req.getContextPath()+"/");



    }

    private void deleteOrders(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String paramPass = req.getParameter("lozinka");

        if(paramPass!=null && paramPass.equals(password)){

            synchronized (this){

                for(String day: days){
                    Map<String,Integer> meals = orders.get(day);

                    for(String meal: meals.keySet()){
                        meals.put(meal,0);
                    }
                }

                counter++;

            }
            resp.sendRedirect(req.getContextPath()+"/admin?lozinka="+password);

        } else
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);


    }




    private String kapitalizuj(String rec) {
        if (rec == null || rec.isEmpty()) return rec;
        return rec.substring(0, 1).toUpperCase() + rec.substring(1);
    }
}