package de.ait.restaurantapp.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Aspect для генерации PlantUML sequence-диаграмм
 * при последовательном вызове контроллеров, сервисов и репозиториев.
 */
@Aspect
@Component
public class SequenceLoggingAspect {

    // PrintWriter, который будет писать в файл sequence.puml
    private final PrintWriter writer;

    /**
     * ThreadLocal-стек для хранения "контекста" вызывающего:
     * - по умолчанию в начале стоит "Client"
     * - при заходе в метод мы пушим в стек имя текущего класса
     * - при выходе — возвращаемся к предыдущему
     */
    private final ThreadLocal<Deque<String>> callStack = ThreadLocal.withInitial(() -> {
        Deque<String> st = new ArrayDeque<>();
        st.push("Client");    // начальный элемент — клиент (внешняя система)
        return st;
    });

    /**
     * Конструктор аспекта:
     * - создаёт (при необходимости) папку для диаграмм
     * - открывает файл sequence.puml для записи (перезаписывая старый)
     * - записывает шапку PlantUML: @startuml, autonumber, настройка толщины стрелок
     */
    public SequenceLoggingAspect() throws IOException {
        File output = new File("src/main/resources/diagrams/sequence.puml");
        output.getParentFile().mkdirs();               // создание папки, если её нет
        writer = new PrintWriter(new FileWriter(output, false));  // открываем на перезапись

        writer.println("@startuml");                    // начало диаграммы
        writer.println("autonumber");                   // автоматическая нумерация шагов
        writer.println("skinparam sequenceArrowThickness 2"); // толщина стрелок
        writer.println();
        writer.flush();                                 // сразу пишем в файл
    }

    /**
     * Pointcut, покрывающий все методы в указанных пакетах:
     * - контроллеры
     * - сервисы
     * - репозитории
     */

    @Pointcut(
            "execution(* de.ait.restaurantapp.controller..*(..)) || " +
                    "execution(* de.ait.restaurantapp.services..*(..))   || " +
                    "execution(* de.ait.restaurantapp.repositories..*(..))"
    )
    public void applicationLayer() {}


    /**
     * Around-советник, который оборачивает каждый метод из applicationLayer():
     * 1) Определяет, кто текущий «caller» (peek из callStack)
     * 2) Пишет в диаграмму стрелку от caller к текущему классу.method()
     * 3) Пушит имя текущего класса в стек
     * 4) Выполняет оригинальный метод через pjp.proceed()
     * 5) При возврате — попает имя текущего класса из стека
     * 6) Пишет стрелку return обратно к previousCaller
     */
    @Around("applicationLayer()")
    public Object logSequence(ProceedingJoinPoint pjp) throws Throwable {
        // Кто вызывал текущий метод?
        String caller = callStack.get().peek();

        // Получаем короткое имя класса и имя метода
        String className = pjp.getSignature().getDeclaringType().getSimpleName();
        String methodName = pjp.getSignature().getName();

        // Записываем «вход» в метод
        writer.printf("%s -> %s: %s()%n", caller, className, methodName);
        writer.flush();

        // Переходим «вглубь» — запоминаем, что теперь контекстом является этот класс
        callStack.get().push(className);

        // Выполняем оригинальный метод
        Object result = pjp.proceed();

        // Завершаем контекст текущего класса и возвращаемся к предыдущему
        callStack.get().pop();
        // Записываем «выход» из метода
//        writer.printf("%s <-- %s: return%n%n", className, caller);
//        writer.flush();

        writer.printf("%s --> %s: return%n%n", className, caller);
        writer.flush();


        return result;
    }

    /**
     * Метод, вызываемый перед уничтожением бина:
     * - дописывает в файл конец диаграммы
     * - закрывает writer
     */
    @PreDestroy
    public void finish() {
        writer.println("@enduml");
        writer.close();
    }
}
