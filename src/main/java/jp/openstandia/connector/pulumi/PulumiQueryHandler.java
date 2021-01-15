package jp.openstandia.connector.pulumi;


@FunctionalInterface
public interface PulumiQueryHandler<T> {
    boolean handle(T arg);
}