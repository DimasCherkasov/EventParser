# Исправление проблемы с аннотациями в проекте EventParser

## Проблема

В проекте EventParser была обнаружена проблема с аннотацией `@PostConstruct` в классе `WebsiteEventParser`. Эта аннотация использовалась для инициализации пула потоков после установки всех свойств класса:

```java
@javax.annotation.PostConstruct
public void init() {
    this.executor = Executors.newFixedThreadPool(threadCount);
    log.info("Initialized thread pool with {} threads", threadCount);
}
```

Проблема заключается в том, что аннотация `@PostConstruct` из пакета `javax.annotation` была удалена из JDK в Java 11, и для ее использования требуется добавить зависимость `javax.annotation-api` в файл `pom.xml`. Однако, при попытке добавить эту зависимость, Maven не смог ее найти.

## Решение

Вместо использования аннотации `@PostConstruct`, мы реализовали интерфейс `InitializingBean` из Spring Framework, который предоставляет метод `afterPropertiesSet()`, выполняющий ту же функцию - инициализацию после установки всех свойств.

### Изменения в коде

1. Добавлен импорт интерфейса `InitializingBean`:
```java
import org.springframework.beans.factory.InitializingBean;
```

2. Класс `WebsiteEventParser` теперь реализует интерфейс `InitializingBean`:
```java
public class WebsiteEventParser implements EventParser, InitializingBean {
}
```

3. Метод `init()` с аннотацией `@PostConstruct` заменен на метод `afterPropertiesSet()`:
```java
@Override
public void afterPropertiesSet() throws Exception {
    this.executor = Executors.newFixedThreadPool(threadCount);
    log.info("Initialized thread pool with {} threads", threadCount);
}
```

## Почему это работает

Интерфейс `InitializingBean` является частью Spring Framework и не требует дополнительных зависимостей, так как Spring Boot уже включен в проект. Метод `afterPropertiesSet()` вызывается контейнером Spring после того, как все свойства бина были установлены, что эквивалентно поведению аннотации `@PostConstruct`.

Это решение обеспечивает правильную инициализацию пула потоков после установки значения свойства `threadCount`, что предотвращает ошибку `IllegalArgumentException`, которая возникала при попытке создать пул потоков с неинициализированным значением `threadCount`.

## Тестирование

Для проверки работоспособности решения необходимо собрать проект с помощью Maven и запустить приложение:

```bash
mvn clean package
java -jar target/event-parser-1.0-SNAPSHOT.jar
```

После запуска приложения в логах должно появиться сообщение "Initialized thread pool with X threads", где X - значение свойства `parser.threads` из файла `application.properties` (по умолчанию 5).

## Заключение

Замена аннотации `@PostConstruct` на реализацию интерфейса `InitializingBean` позволяет избежать проблем с зависимостями и обеспечивает корректную работу приложения в Java 11 и выше. Это решение является более надежным и не требует дополнительных зависимостей.
