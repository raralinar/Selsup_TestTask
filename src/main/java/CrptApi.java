import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class CrptApi {
    private final int requestLimit;
    private final TimeUnit timeUnit;
    private AtomicInteger remainingRequest;
    private long requestTime;
    private String API = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    public CrptApi(int requestLimit, TimeUnit tu) {
        this.requestLimit = requestLimit;
        this.timeUnit = tu;
        this.remainingRequest = new AtomicInteger(requestLimit);
        this.requestTime = System.currentTimeMillis();
    }

    public synchronized void createDoc(Document document, String name) throws InterruptedException, IOException, ParseException {
        long takenTime = System.currentTimeMillis() - requestTime;
        if (timeUnit.toMillis(takenTime) >= timeUnit.toMillis(1)) {
            remainingRequest.set(requestLimit);
            requestTime = System.currentTimeMillis();
        }
        if (!isRequestAllowed()) {
            long sleep = timeUnit.toMillis(1) - takenTime;
            if (sleep > 0)
                Thread.sleep(sleep);
            remainingRequest.set(requestLimit);
            requestTime = System.currentTimeMillis();
        }

        String result;

        HttpPost post = new HttpPost(API);
        StringEntity payload = new StringEntity(document.convertToJsonString());
        post.setEntity(payload);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(post)) {
            result = EntityUtils.toString(response.getEntity());
        }
        Logger logger = Logger.getLogger(CrptApi.class);
        logger.info("Server response: " + result);

        remainingRequest.decrementAndGet();
    }

    private boolean isRequestAllowed() {
        return remainingRequest.get() > 0;
    }

    private static JSONObject createJson(String s) throws ParseException {
        JSONParser parser = new JSONParser();
        Object jsonObj = parser.parse(s);
        return (JSONObject) jsonObj;
    }

    interface JsonConvertable {
        String convertToJsonString();
    }


    static class Document implements JsonConvertable {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn, participant_inn, producer_inn;
        private Date production_date;
        private String production_type;
        private List<Product> list;
        private Date reg_date;
        private String reg_number;

        @Override
        public String convertToJsonString() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }
    @AllArgsConstructor
    static class Product {
        private String certificate_document;
        private Date certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private Date production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    @AllArgsConstructor
    static class Description {
        private String participantInn;
    }
}
