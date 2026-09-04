package io.github.bigfiiish.crawlforge.career;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SkillCatalog {
    private final Map<String, Pattern> patterns = new LinkedHashMap<>();

    public SkillCatalog() {
        add("Java", "java"); add("Python", "python"); add("Go", "golang|\bgo\b");
        add("JavaScript", "javascript"); add("TypeScript", "typescript"); add("C++", "c\\+\\+");
        add("C#", "c#|c sharp"); add("SQL", "sql"); add("Spring Boot", "spring(?: boot)?");
        add("React", "react(?:\\.js)?"); add("Angular", "angular"); add("Node.js", "node(?:\\.js)?");
        add("AWS", "aws|amazon web services"); add("Azure", "azure"); add("GCP", "gcp|google cloud");
        add("Docker", "docker"); add("Kubernetes", "kubernetes|\\bk8s\\b"); add("Terraform", "terraform");
        add("Kafka", "kafka"); add("Redis", "redis"); add("PostgreSQL", "postgres(?:ql)?");
        add("MySQL", "mysql"); add("MongoDB", "mongodb"); add("Elasticsearch", "elasticsearch|elastic search");
        add("Spark", "apache spark|\\bspark\\b"); add("Flink", "flink"); add("Airflow", "airflow");
        add("Hadoop", "hadoop"); add("Snowflake", "snowflake"); add("Databricks", "databricks");
        add("REST", "rest(?:ful)? api"); add("GraphQL", "graphql"); add("gRPC", "grpc");
        add("Microservices", "microservices?"); add("Distributed Systems", "distributed systems?");
        add("CI/CD", "ci/cd|continuous integration|continuous delivery");
        add("Git", "\\bgit\\b|github|gitlab"); add("Linux", "linux");
        add("Machine Learning", "machine learning|\\bml\\b"); add("LLM", "large language model|\\bllms?\\b");
        add("RAG", "retrieval.augmented generation|\\brag\\b"); add("PyTorch", "pytorch");
        add("TensorFlow", "tensorflow"); add("Pandas", "pandas"); add("NumPy", "numpy");
    }

    private void add(String name, String expression) {
        patterns.put(name, Pattern.compile("(?i)(?:^|[^a-z0-9])(?:" + expression + ")(?:$|[^a-z0-9])"));
    }

    public List<String> detect(String text) {
        String source = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        patterns.forEach((name, pattern) -> { if (pattern.matcher(source).find()) found.add(name); });
        return List.copyOf(found);
    }
}
