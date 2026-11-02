package org.springy.som.nlp.judge.internal;

import org.springy.som.nlp.judge.RequestType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * The {@code judges} collection. {@code JudgeCatalog} enforces unique names.
 */
public interface JudgeRepository extends MongoRepository<RequestType, String> {

    Optional<RequestType> findByName(String name);

    boolean existsByName(String name);

    void deleteByName(String name);
}
