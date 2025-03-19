package com.nona.inf.persistence;

import lombok.extern.slf4j.Slf4j;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.ListCompareAlgorithm;
import org.javers.core.metamodel.clazz.EntityDefinitionBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Configuration
@Slf4j
public class persistConfig {
    @Bean
    public Javers javers() {
        final JaversBuilder javersBuilder = JaversBuilder
                .javers()
                .withListCompareAlgorithm(ListCompareAlgorithm.LEVENSHTEIN_DISTANCE);

        final Set<Class<?>> classes = scanAllDomainEntities("com.nona.domain.**.entity");
        for (Class<?> clazz : classes) {
            javersBuilder.registerEntity(EntityDefinitionBuilder
                    .entityDefinition(clazz)
                    .withIdPropertyName("id")
                    .withTypeName(clazz.getSimpleName())
                    .build());
            if (log.isDebugEnabled()) {
                log.debug("javers register domain entity:{}", clazz);
            }
        }
        return javersBuilder.build();
    }


    private Set<Class<?>> scanAllDomainEntities(String basePath) {
        final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        final String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + ClassUtils.convertClassNameToResourcePath(basePath) + "/**/*.class";
        final HashSet<Class<?>> classes = new HashSet<>();
        try {
            final CachingMetadataReaderFactory factory = new CachingMetadataReaderFactory();
            for (Resource resource : resolver.getResources(pattern)) {
                if (resource.isReadable()) {
                    final MetadataReader reader = factory.getMetadataReader(resource);
                    final ClassMetadata classMetadata = reader.getClassMetadata();
                    if (classMetadata.isAbstract() || classMetadata.isInterface()) {
                        continue;
                    }
                    String className = classMetadata.getClassName();
                    final Class<?> clazz = Class.forName(className);
                    if (clazz.isAnonymousClass() || clazz.isLocalClass() || clazz.isEnum() || clazz.isRecord() || clazz.getEnclosingClass() != null) {
                        continue;
                    }
                    classes.add(clazz);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return classes;
    }
}
