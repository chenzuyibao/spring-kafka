/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.kafka.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

import org.apache.kafka.common.TopicPartition;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.adapter.DeDuplicationStrategy;
import org.springframework.kafka.support.converter.MessageConverter;
import org.springframework.util.Assert;

/**
 * Base model for a Kafka listener endpoint.
 *
 * @param <K> the key type.
 * @param <V> the value type.
 *
 * @author Stephane Nicoll
 * @author Gary Russell
 *
 * @see MethodKafkaListenerEndpoint
 * @see org.springframework.kafka.config.SimpleKafkaListenerEndpoint
 */
public abstract class AbstractKafkaListenerEndpoint<K, V>
		implements KafkaListenerEndpoint, BeanFactoryAware, InitializingBean {

	private String id;

	private final Collection<String> topics = new ArrayList<>();

	private Pattern topicPattern;

	private final Collection<TopicPartition> topicPartitions = new ArrayList<>();

	private BeanFactory beanFactory;

	private BeanExpressionResolver resolver;

	private BeanExpressionContext expressionContext;

	private String group;

	private DeDuplicationStrategy<K, V> deDuplicationStrategy;


	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
		if (beanFactory instanceof ConfigurableListableBeanFactory) {
			this.resolver = ((ConfigurableListableBeanFactory) beanFactory).getBeanExpressionResolver();
			this.expressionContext = new BeanExpressionContext((ConfigurableListableBeanFactory) beanFactory, null);
		}
	}

	protected BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	protected BeanExpressionResolver getResolver() {
		return this.resolver;
	}

	protected BeanExpressionContext getBeanExpressionContext() {
		return this.expressionContext;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return this.id;
	}

	/**
	 * Set the topics to use. Either these or 'topicPattern' or 'topicPartitions'
	 * should be provided, but not a mixture.
	 * @param topics to set.
	 * @see #setTopicPartitions(TopicPartition...)
	 * @see #setTopicPattern(Pattern)
	 */
	public void setTopics(String... topics) {
		Assert.notNull(topics, "'topics' must not be null");
		this.topics.clear();
		this.topics.addAll(Arrays.asList(topics));
	}

	/**
	 * Return the topics for this endpoint.
	 * @return the topics for this endpoint.
	 */
	@Override
	public Collection<String> getTopics() {
		return Collections.unmodifiableCollection(this.topics);
	}

	/**
	 * Set the topics to use. Either these or 'topicPattern'
	 * or 'topicPartitions'
	 * should be provided, but not a mixture.
	 * @param topicPartitions to set.
	 * @see #setTopics(String...)
	 * @see #setTopicPattern(Pattern)
	 */
	public void setTopicPartitions(TopicPartition... topicPartitions) {
		Assert.notNull(topicPartitions, "'topics' must not be null");
		this.topicPartitions.clear();
		this.topicPartitions.addAll(Arrays.asList(topicPartitions));
	}

	/**
	 * Return the topicPartitions for this endpoint.
	 * @return the topicPartitions for this endpoint.
	 */
	@Override
	public Collection<TopicPartition> getTopicPartitions() {
		return Collections.unmodifiableCollection(this.topicPartitions);
	}

	/**
	 * Set the topic pattern to use. Cannot be used with
	 * topics or topicPartitions.
	 * @param topicPattern the pattern
	 * @see #setTopicPartitions(TopicPartition...)
	 * @see #setTopics(String...)
	 */
	public void setTopicPattern(Pattern topicPattern) {
		this.topicPattern = topicPattern;
	}

	/**
	 * Return the topicPattern for this endpoint.
	 * @return the topicPattern for this endpoint.
	 */
	@Override
	public Pattern getTopicPattern() {
		return this.topicPattern;
	}

	@Override
	public String getGroup() {
		return this.group;
	}

	/**
	 * Set the group for the corresponding listener container.
	 * @param group the group.
	 */
	public void setGroup(String group) {
		this.group = group;
	}

	@Override
	public void afterPropertiesSet() {
		boolean topicsEmpty = getTopics().isEmpty();
		boolean topicPartitionsEmpty = getTopicPartitions().isEmpty();
		if (!topicsEmpty && !topicPartitionsEmpty) {
			throw new IllegalStateException("Topics or topicPartitions must be provided but not both for " + this);
		}
		if (this.topicPattern != null && (!topicsEmpty || !topicPartitionsEmpty)) {
			throw new IllegalStateException("Only one of topics, topicPartitions or topicPattern must are allowed for "
						+ this);
		}
		if (this.topicPattern == null && topicsEmpty && topicPartitionsEmpty) {
			throw new IllegalStateException("At least one of topics, topicPartitions or topicPattern must be provided "
					+ "for " + this);
		}
	}

	protected DeDuplicationStrategy<K, V> getDeDuplicationStrategy() {
		return this.deDuplicationStrategy;
	}

	/**
	 * Set a {@link DeDuplicationStrategy} implementation.
	 * @param deDuplicationStrategy the strategy implementation.
	 */
	public void setDeDuplicationStrategy(DeDuplicationStrategy<K, V> deDuplicationStrategy) {
		this.deDuplicationStrategy = deDuplicationStrategy;
	}

	@Override
	public void setupListenerContainer(MessageListenerContainer listenerContainer, MessageConverter messageConverter) {
		setupMessageListener(listenerContainer, messageConverter);
	}

	/**
	 * Create a {@link MessageListener} that is able to serve this endpoint for the
	 * specified container.
	 * @param container the {@link MessageListenerContainer} to create a {@link MessageListener}.
	 * @param messageConverter the message converter - may be null.
	 * @return a a {@link MessageListener} instance.
	 */
	protected abstract MessageListener<K, V> createMessageListener(MessageListenerContainer container,
			MessageConverter messageConverter);

	private void setupMessageListener(MessageListenerContainer container, MessageConverter messageConverter) {
		MessageListener<K, V> messageListener = createMessageListener(container, messageConverter);
		Assert.state(messageListener != null, "Endpoint [" + this + "] must provide a non null message listener");
		container.setupMessageListener(messageListener);
	}

	/**
	 * Return a description for this endpoint.
	 * @return a description for this endpoint.
	 * <p>Available to subclasses, for inclusion in their {@code toString()} result.
	 */
	protected StringBuilder getEndpointDescription() {
		StringBuilder result = new StringBuilder();
		return result.append(getClass().getSimpleName()).append("[").append(this.id).
				append("] topics=").append(this.topics).
				append("' | topicPartitions='").append(this.topicPartitions).
				append("' | topicPattern='").append(this.topicPattern).append("'");
	}

	@Override
	public String toString() {
		return getEndpointDescription().toString();
	}

}
