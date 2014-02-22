/**
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.support.mapping;

import org.neo4j.graphdb.Node;
import org.springframework.context.ApplicationListener;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.mapping.context.MappingContextEvent;
import org.springframework.data.neo4j.core.TypeRepresentationStrategy;
import org.springframework.data.neo4j.mapping.Neo4jPersistentEntity;
import org.springframework.data.neo4j.mapping.Neo4jPersistentProperty;
import org.springframework.data.neo4j.support.index.IndexProvider;
import org.springframework.data.neo4j.support.index.IndexType;
import org.springframework.data.neo4j.support.schema.SchemaIndexProvider;

/**
 * @author mh
 * @since 12.04.12
 */
public class IndexCreationMappingEventListener implements ApplicationListener<MappingContextEvent<Neo4jPersistentEntity<?>, Neo4jPersistentProperty>> {
    private IndexProvider indexProvider;
    private SchemaIndexProvider schemaIndexProvider;
    private TypeRepresentationStrategy<Node> nodeTypeRepresentationStrategy;

    public IndexCreationMappingEventListener(IndexProvider indexProvider, SchemaIndexProvider schemaIndexProvider, TypeRepresentationStrategy<Node> nodeTypeRepresentationStrategy) {
        this.indexProvider = indexProvider;
        this.schemaIndexProvider = schemaIndexProvider;
        this.nodeTypeRepresentationStrategy = nodeTypeRepresentationStrategy;
    }

    @Override
    public void onApplicationEvent(MappingContextEvent<Neo4jPersistentEntity<?>, Neo4jPersistentProperty> event) {
        if (!(event.getSource() instanceof Neo4jMappingContext)) return;
        final Neo4jPersistentEntity entity = event.getPersistentEntity();
        ensureEntityIndexes(entity);
    }

    private void ensureEntityIndexes(Neo4jPersistentEntity<?> entity) {
        final Class entityType = entity.getType();

        // TODO only when TRS is non-label? I think we prob need to do it
        //      anyway as you can still mix legacy indexes with label based
        //      ones???
        // Pass 1 - do schema based updates first to prevent locking
        entity.doWithProperties(new PropertyHandler<Neo4jPersistentProperty>() {
            @Override
            public void doWithPersistentProperty(Neo4jPersistentProperty property) {
                if (property.isIndexed() && property.getIndexInfo().isLabelBased()) {
                    schemaIndexProvider.createIndex(property);
                }
            }
        });

        // Pass 2 - do everything else
        indexProvider.getIndex(entity, null, IndexType.SIMPLE);
        entity.doWithProperties(new PropertyHandler<Neo4jPersistentProperty>() {
            @Override
            public void doWithPersistentProperty(Neo4jPersistentProperty property) {
                if (property.isIndexed() && !property.getIndexInfo().isLabelBased()){
                    indexProvider.getIndex(property, entityType);
                }
            }
        });
    }
}
