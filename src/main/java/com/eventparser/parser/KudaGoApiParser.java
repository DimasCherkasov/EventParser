package com.eventparser.parser;

import com.eventparser.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Реализация EventParser для получения мероприятий через API KudaGo.
 * Использует официальное API вместо парсинга HTML.
 * 
 * Документация API: https://docs.kudago.com/api/#api-events-list
 */
@Slf4j
@Component
public class KudaGoApiParser implements EventParser, InitializingBean {

    private static final String API_BASE_URL = "https://kudago.com/public-api/v1.4";
    private static final String DEFAULT_CITY = "msk"; // Москва
    private static final String DEFAULT_CONTACT = "info@kudago.com";

    private final RestTemplate restTemplate;
    private Executor executor;

    @Value("${parser.threads:5}")
    private int threadCount;

    @Autowired
    public KudaGoApiParser(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.executor = Executors.newFixedThreadPool(threadCount);
        log.info("Initialized KudaGoApiParser with {} threads", threadCount);
    }

    @Override
    public List<Event> parseEvents(String source) {
        log.info("Parsing events from KudaGo API");
        List<Event> events = new ArrayList<>();

        try {
            // Определяем город из источника или используем значение по умолчанию
            String city = DEFAULT_CITY;
            if (source.contains("/")) {
                String[] parts = source.split("/");
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].equals("msk") || parts[i].equals("spb") || parts[i].equals("nsk")) {
                        city = parts[i];
                        break;
                    }
                }
            }

            // Формируем URL для запроса к API
            String apiUrl = String.format("%s/events/?location=%s&fields=id,title,description,dates,place,price,images", 
                    API_BASE_URL, city);

            log.info("Requesting events from KudaGo API: {}", apiUrl);

            // Выполняем запрос к API
            ResponseEntity<Map> response = restTemplate.getForEntity(apiUrl, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && responseBody.containsKey("results")) {
                List<Map<String, Object>> results = (List<Map<String, Object>>) responseBody.get("results");
                log.info("Received {} events from KudaGo API", results.size());

                for (Map<String, Object> eventData : results) {
                    try {
                        Event event = mapKudaGoEventToEvent(eventData, city);
                        if (event != null) {
                            events.add(event);
                        }
                    } catch (Exception e) {
                        log.error("Error mapping KudaGo event: {}", e.getMessage(), e);
                    }
                }
            }

            log.info("Parsed {} events from KudaGo API", events.size());
        } catch (Exception e) {
            log.error("Error parsing events from KudaGo API: {}", e.getMessage(), e);
        }

        return events;
    }

    @Override
    public CompletableFuture<List<Event>> parseEventsAsync(String source) {
        return CompletableFuture.supplyAsync(() -> parseEvents(source), executor);
    }

    /**
     * Преобразует данные о мероприятии из API KudaGo в объект Event.
     * 
     * @param eventData Данные о мероприятии из API KudaGo
     * @param city Город, для которого запрашиваются мероприятия
     * @return Объект Event или null, если преобразование не удалось
     */
    private Event mapKudaGoEventToEvent(Map<String, Object> eventData, String city) {
        try {
            // Получаем основные данные о мероприятии
            Integer id = (Integer) eventData.get("id");
            String title = (String) eventData.get("title");

            if (title == null || title.isEmpty()) {
                log.warn("Skipping KudaGo event with no title");
                return null;
            }

            // Получаем даты проведения мероприятия
            List<Map<String, Object>> dates = (List<Map<String, Object>>) eventData.get("dates");
            if (dates == null || dates.isEmpty()) {
                log.warn("Skipping KudaGo event with no dates");
                return null;
            }

            // Берем первую дату из списка
            Map<String, Object> dateInfo = dates.get(0);
            Long startTimestamp = (Long) dateInfo.get("start");
            LocalDateTime startDate = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(startTimestamp), 
                    ZoneId.systemDefault());

            // Получаем место проведения
            Map<String, Object> place = (Map<String, Object>) eventData.get("place");
            String location = "Москва"; // Значение по умолчанию

            if (place != null && place.containsKey("title")) {
                location = (String) place.get("title");

                if (place.containsKey("address")) {
                    String address = (String) place.get("address");
                    if (address != null && !address.isEmpty()) {
                        location += ", " + address;
                    }
                }
            }

            // Получаем цену
            Map<String, Object> priceInfo = (Map<String, Object>) eventData.get("price");
            BigDecimal price = null;

            if (priceInfo != null && priceInfo.containsKey("min")) {
                Object minPrice = priceInfo.get("min");
                if (minPrice instanceof Integer) {
                    price = new BigDecimal((Integer) minPrice);
                } else if (minPrice instanceof Double) {
                    price = BigDecimal.valueOf((Double) minPrice);
                }
            }

            // Формируем URL источника
            String sourceUrl = String.format("https://kudago.com/%s/event/%d/", city, id);

            // Создаем объект Event
            return Event.builder()
                    .name(title)
                    .date(startDate)
                    .location(location)
                    .price(price)
                    .organizerName("KudaGo")
                    .organizerContact(DEFAULT_CONTACT)
                    .sourceUrl(sourceUrl)
                    .build();
        } catch (Exception e) {
            log.error("Error mapping KudaGo event: {}", e.getMessage(), e);
            return null;
        }
    }
}
