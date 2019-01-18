package com.jarvisk.timeoutexam;

import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Slf4j
@RestController
@SpringBootApplication
public class TimeoutExamApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(TimeoutExamApplication.class, args);
    }

    @GetMapping(path = "/sleep1")
    public String sleep(@RequestParam(name = "second", required = false, defaultValue = "5") int second) throws InterruptedException {
        StopWatch stopWatch = new StopWatch("sleep 1");
        stopWatch.start("thread sleep 1");

        try {
            log.info("start sleep on sleep1");
            TimeUnit.SECONDS.sleep(second);
        } catch (InterruptedException e) {
            log.error("interrupted Exception", e);
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            stopWatch.stop();
            log.info("sleep 1: waiting in {} second \n {}", second, stopWatch.prettyPrint());
            log.info("end sleep on sleep1");
        }

        return "ok:" + second;
    }

    @GetMapping(path = "/sleep2")
    public void sleep2(@RequestParam(name = "second", required = false, defaultValue = "5") int second,
                        HttpServletResponse response) throws InterruptedException, IOException {
        StopWatch stopWatch = new StopWatch("sleep 2");
        stopWatch.start("thread sleep 2");

        try {
            TimeUnit.SECONDS.sleep(second);
        } catch (InterruptedException e) {
            log.error("interrupted Exception", e);
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            stopWatch.stop();
            log.info("sleep 2: waiting in {} second \n {}", second, stopWatch.prettyPrint());
        }

        var os = response.getOutputStream();
        ByteArrayInputStream is = new ByteArrayInputStream(("ok:" + second).getBytes());
        IOUtils.copy(is, os);
        response.flushBuffer();

        ByteArrayInputStream is2 = new ByteArrayInputStream(("ok2:" + second).getBytes());
        IOUtils.copy(is2, os);
        response.flushBuffer();
    }

    @GetMapping(path = "/sleep3")
    public String sleep3(@RequestParam(name = "second", required = false, defaultValue = "5") int second,
                         @RequestParam(name = "loop", required = false, defaultValue = "1000") int loop) throws InterruptedException {
        StopWatch stopWatch = new StopWatch("sleep 3");
        stopWatch.start("thread sleep 3");

        try {
            TimeUnit.SECONDS.sleep(second);
        } catch (InterruptedException e) {
            log.error("interrupted Exception", e);
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            stopWatch.stop();
            log.info("sleep 3: waiting in {} second \n {}", second, stopWatch.prettyPrint());
        }

        StringBuilder builder = new StringBuilder("Hello, World@");
        IntStream.rangeClosed(1, loop)
                .forEach(value -> builder.append("Hello, World:").append(value));

        return "ok:" + builder.toString();
    }

    @GetMapping(path = "/proxy-sleep")
    public void proxySleep(@RequestParam(name = "second", required = false, defaultValue = "5") int second,
                           @RequestParam(name = "loop", required = false, defaultValue = "1000") int loop,
                           HttpServletResponse response) throws IOException {
        log.info("proxy sleep start");
        StopWatch stopWatch = new StopWatch("proxy sleep");
        stopWatch.start("proxy sleep");
        URL url = new URL("http://localhost:8080/sleep-proxied?second=" + second + "&loop=" + loop);
        URLConnection connection = url.openConnection();
        var is = connection.getInputStream();
        var os = response.getOutputStream();
        IOUtils.copy(is, os);       // expected IO Exception: Broken Pipe
        stopWatch.stop();
        response.flushBuffer();
        log.info("proxy sleep end");
        log.info("proxy sleep \n {}", stopWatch.prettyPrint());
    }

    @GetMapping(path = "/sleep-proxied")
    public String sleepProxied(@RequestParam(name = "second", required = false, defaultValue = "5") int second,
                               @RequestParam(name = "loop", required = false, defaultValue = "1000") int loop) throws InterruptedException {
        StringBuilder builder = new StringBuilder("sleep-proxied:1-");
        try {
            TimeUnit.SECONDS.sleep(second);
            IntStream.rangeClosed(1, loop)
                    .forEach(value -> builder.append("sleep-proxied:").append(2));
        } catch (InterruptedException e) {
            log.error("interrupted Exception", e);
            Thread.currentThread().interrupt();
            throw e;
        }

        return "ok:" + builder.toString();
    }


    @ExceptionHandler(InterruptedException.class)
    public ResponseEntity handleInterruptException(InterruptedException e) {
        log.error("interrupted catch in handler", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("interrupted!!! message: " + e.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity handleInterruptException(IOException e) {
        log.error("occur IO Exception catch in handler", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("interrupted!!! message: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity handleException(Exception e) {
        log.error("exception catch in handler", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("occured !!! message: " + e.getMessage());
    }

    @Autowired
    private Environment env;

    @Override
    public void run(String... args) throws Exception {
        log.info("server.connection-timeout: {} second", env.getProperty("server.connection-timeout"));
        log.info("server.tomcat.max-connections: {}", env.getProperty("server.tomcat.max-connections"));
        log.info("server.tomcat.max-threads: {}", env.getProperty("server.tomcat.max-threads"));
        log.info("server.tomcat.min-spare-threads: {}", env.getProperty("server.tomcat.min-spare-threads"));
    }
}

