package io.mysql4j.spring;

import java.util.List;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

class EmbeddedMySQLContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
            List<ContextConfigurationAttributes> configAttributes) {
        AutoConfigureEmbeddedMySQL ann =
                AnnotatedElementUtils.findMergedAnnotation(testClass, AutoConfigureEmbeddedMySQL.class);
        return ann == null ? null : new EmbeddedMySQLContextCustomizer(ann.version(), ann.database(), ann.extraArgs());
    }
}
