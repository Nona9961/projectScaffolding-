package com.nona.inf.persistence.tracking;

import com.nona.changeTracking.domain.model.unitofwork.UnitOfWork;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import com.nona.annotation.ScaffoldGenerated;

@AutoConfiguration
@ConditionalOnClass({UnitOfWork.class, UnitOfWorkProvider.class})
@EnableConfigurationProperties(ChangeTrackingProperties.class)
@ScaffoldGenerated
public class UnitOfWorkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UnitOfWorkProvider unitOfWorkProvider(ChangeTrackingProperties props) {
        return UnitOfWorkProvider.builder()
                .fromProperties(props)
                .build();
    }
}
