package dfm.medischeduler_routeservice; 

public class AuthenticationFilter extends GenericFilterBean { 
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
        throws IOException, ServletException { 
            try {
                Authentication authentication = AuthenticationService.getAuthentication((HttpServletRequest) request); 
                SecurityContextHolder.getContext().setAuthentication(authentication);
                filterChain.doFilter(request, response);
            } catch (Exception exp){
                HttpServletResponse httpResponse = (HttpServletResponse) response; 
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED); 
                httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE); 
                PrintWriter write = httpResponse.getWriter(); 
                writer.print(exp.getMessage()); 
                writer.flush();
                writer.close(); 
            }
        }
    
    public class AuthenticationService { 
        private static final String AUTH_TOKEN_HEADER_NAME = "X-API-KEY"; 
        private static final String AUTH_TOKEN = 'placeholder';
        //TODO FINISH, decide to manage 

    }
}