package com.jarvisk.timeoutexam;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.IntStream;

//@RunWith(SpringRunner.class)
//@SpringBootTest
public class TimeoutExamApplicationTests {

    @Test
    public void contextLoads() {
    }

    /**
     * 결과
     * 클라이언트 입장에서는 Read Timeout 시간 안에 response가 없다면
     * I/O error on GET request for "http://localhost:8080/sleep2": Read timed out; nested exception is java.net.SocketTimeoutException: Read timed out
     * 발생함.
     *
     *
     * 서버입장에서는 클라이언트가 끊어졌어도 진행하던 로직을 수행하고 response 에 Write 함. Spring에서 결과를 Return(write)해도 **Exception 발생하지 않음.**
     * (스프링을 사용했을땐는 Exception이 발생하지 않았지만, Servlet 만 사용했을때는 어떻게 될지 모르겠음.
     * 뭐..끊어졌다면 Spring이 알아서 잘 해주겠지...)
     *
     * 기대했던 InterruptException이라던지, Response 보낼때 Exception 이라던지 발생 안함.
     */
    @Test
    public void sleep1_excepted_read_timeout() {
        HttpComponentsClientHttpRequestFactory clientRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientRequestFactory.setReadTimeout(1000 * 2);
        RestTemplate restTemplate = new RestTemplate(clientRequestFactory);
        var response = restTemplate.getForEntity("http://localhost:8080/sleep1?second=" + 10, String.class);

        System.out.println("response status: " + response.getStatusCode());
        System.out.println("response body" + response.getBody());
    }

    /**
     * 결과를 ServletResponse 에 바로 써봤지만 위와 결과는 동일함.
     */
    @Test
    public void sleep2_excepted_read_timeout() {
        HttpComponentsClientHttpRequestFactory clientRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientRequestFactory.setReadTimeout(1000 * 2);
        RestTemplate restTemplate = new RestTemplate(clientRequestFactory);
        var response = restTemplate.getForEntity("http://localhost:8080/sleep2?second=" + 10, String.class);

        System.out.println("response status: " + response.getStatusCode());
        System.out.println("response body" + response.getBody());
    }


    /**
     * 클라이언트는 sleep1, 2와 동일하게 에러 발생.
     *
     * 서버에서는 org.apache.catalina.connector.ClientAbortException: java.io.IOException: Broken pipe 발생
     * 예상으로는 sleep1, 2는 response를 처음 flush 했는데(response에 ), 클라이언트가 종료 한것을 캐치하고 Exception을 발생시키지 않았지만
     * **(처음 flush 할때 발생하지 않는것은 서블릿이 커넥션이 끊긴걸 감지하고 커넥션을 닫은것인가...
     * 아니면 서블릿은 Socket으로 데이터를 보냈는데 Socket에서 커넥션 끊겨 Exception이 발생하고, 처음 발생한 Exception을 서블릿이 캐치한것일까... )**, <- 이건 어떻게 테스트를..?
     * 끊긴 OutputStream에 계속 데이터를 쓰려고하여 I/O Exception: Broken Pipe가 발생된것으로 보임.
     * sleep1,2와 다르게 데이터가 클라이언트로 보낼 데이터가 많아서 여러번 flush 하기때문 위와 다르게 발생되는것으로 파악된.
     *
     * sleep2에서 flush 이후 또한번 flush하면 동일하게 발생될것으로 예상함. 해봐야지
     * 결과: 예상되로 java.io.IOException: Broken pipe 발생함. >_<
     *
     * 결론적으로
     * Read timed out은 클라이언트가 서버에서 응답이 없어 커넥션을 끊고 클라이언트에서 Exception 발생시킨것.
     * Broken Pipe는 서버에서 클라이언트로 데이터를 보내려고 했는데 클라이언트가 커넥션을 끊어서 발생한것.
     */
    @Test
    public void sleep3_excepted_read_timeout_and_server_is_Broken_Pipe() {
        HttpComponentsClientHttpRequestFactory clientRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientRequestFactory.setReadTimeout(1000 * 2);
        RestTemplate restTemplate = new RestTemplate(clientRequestFactory);
        var response = restTemplate.getForEntity("http://localhost:8080/sleep3?second=" + 10, String.class);

        System.out.println("response status: " + response.getStatusCode());
        System.out.println("response body" + response.getBody());
    }


    @Test
    public void proxy_sleep() {
        HttpComponentsClientHttpRequestFactory clientRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientRequestFactory.setReadTimeout(1000 * 2);
        RestTemplate restTemplate = new RestTemplate(clientRequestFactory);
        var response = restTemplate.getForEntity("http://localhost:8080/proxy-sleep?second=" + 10, String.class);

        System.out.println("response status: " + response.getStatusCode());
        System.out.println("response body" + response.getBody());
    }

    @Test
    public void connection_timeout_test() throws InterruptedException {
        HttpClient httpClient = HttpClientBuilder.create()
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(100)
                .build();
        HttpComponentsClientHttpRequestFactory clientRequestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate restTemplate = new RestTemplate(clientRequestFactory);
        List<Integer> okResults = new CopyOnWriteArrayList<>();
        List<Integer> noResponseResults = new CopyOnWriteArrayList<>();

        ExecutorService es = Executors.newCachedThreadPool();
        CountDownLatch latch = new CountDownLatch(10);
        IntStream.rangeClosed(1, 10)
                .forEach(value -> es.submit(() -> {
                    try {
                        System.out.println("start connection time test request " + value);
                        var response = restTemplate.getForEntity("http://localhost:8080/sleep1?second=" + 3, String.class);

                        System.out.println("value: " + value + " response status: " + response.getStatusCode());
                        System.out.println("value: " + value + " response body" + response.getBody());
                        okResults.add(value);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println(e.getMessage());
                        noResponseResults.add(value);
                    } finally {
                        latch.countDown();
                    }
                }));


        latch.await();
        System.out.println("ok result count : " + okResults.size());
        System.out.println("no response result count : " + noResponseResults.size());
    }
}

