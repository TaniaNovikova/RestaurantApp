package de.ait.restaurantapp.aop;

import jakarta.annotation.*;
import org.aspectj.lang.*;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.*;

import java.io.*;

@Aspect
@Component
public class SequenceLoggingAspect {

    private final PrintWriter writer;

    public SequenceLoggingAspect() throws IOException {
        File output = new File("src/main/resources/diagrams/sequence.puml");
        output.getParentFile().mkdirs();  // Создаём папки, если их нет
        writer = new PrintWriter(new FileWriter(output, false));
        // Инициализация PlantUML
        writer.println("@startuml");
        writer.println("autonumber");      // автоматическая нумерация шагов
        writer.println("skinparam sequenceArrowThickness 2");
        writer.println();                  // пустая строка для читабельности
        writer.flush();
    }

    // Точка среза: контроллеры, сервисы и репозитории
    @Pointcut("within(de.ait.restaurantapp.controller..*) || within(de.ait.restaurantapp.services..*) || within(de.ait.restaurantapp.repositories..*)")
    public void applicationLayer() {}

    // Логируем входящие и возвращаемые сообщения
    @Around("applicationLayer()")
    public Object logSequence(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getSignature().getDeclaringType().getSimpleName();
        String methodName = pjp.getSignature().getName();

        // До вызова метода
        writer.printf("Client -> %s: %s()%n", className, methodName);
        writer.flush();

        Object result = pjp.proceed();

        // После вызова метода
        writer.printf("Client <-- %s: return%n%n", className);
        writer.flush();

        return result;
    }

    @PreDestroy
    public void finish() {
        writer.println("@enduml");
        writer.close();
    }
}
