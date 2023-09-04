package org.proxyangel;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancerServer {

    // A list of backend servers to balance the load
    private static final List<String> BACKENDS = new ArrayList<>();

    // A counter to keep track of the current backend server
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    // A Jetty HTTP client to send requests to the backend servers
    private static final HttpClient CLIENT = new HttpClient();

    public static void main(String[] args) throws Exception {
        // Add some backend servers for demonstration purposes
        BACKENDS.add("http://localhost:9090");
//        BACKENDS.add("http://localhost:8082");
//        BACKENDS.add("http://localhost:8083");

        // Start the HTTP client
        CLIENT.start();

        // Create a Jetty server for the load balancer
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(8080);
        server.addConnector(connector);

        // Set a handler that forwards the requests to the backend servers
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
                // Get the next backend server to use
                String backend = getNextBackend();

                // Create a new request to the backend server
                Request proxyRequest = CLIENT.newRequest(backend + target);

                // Copy the headers and content from the original request
//                proxyRequest.header(headers -> headers.addAll(request.getHeaderNames(), name -> request.getHeaders(name)));

                // Copy the headers from the original request
                Enumeration<String> headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    Enumeration<String> headerValues = request.getHeaders(headerName);
                    while (headerValues.hasMoreElements()) {
                        String headerValue = headerValues.nextElement();
                        proxyRequest.header(headerName, headerValue);
                    }
                }


                proxyRequest.content(new BytesContentProvider(request.getInputStream().readAllBytes()));

                // Set a timeout for the proxy request
                proxyRequest.timeout(10, TimeUnit.SECONDS);

                try {
                    // Send the proxy request and get the response
                    ContentResponse proxyResponse = proxyRequest.send();

                    // Copy the status, headers and content from the proxy response
                    response.setStatus(proxyResponse.getStatus());
                    response.setContentType(proxyResponse.getMediaType());
                    response.getOutputStream().write(proxyResponse.getContent());

                    // Mark the request as handled
                    baseRequest.setHandled(true);
                } catch (Exception e) {
                    // Handle any exceptions that may occur
                    e.printStackTrace();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                }
            }
        });

        // Start the load balancer server
        server.start();
        server.join();
    }

    // A method that returns the next backend server in a round-robin fashion
    private static String getNextBackend() {
        int index = COUNTER.getAndIncrement() % BACKENDS.size();
        return BACKENDS.get(index);
    }
}
