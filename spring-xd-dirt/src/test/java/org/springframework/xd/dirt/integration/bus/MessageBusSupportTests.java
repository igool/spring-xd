/*
 * Copyright 2013 the original author or authors.
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

package org.springframework.xd.dirt.integration.bus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.ContentTypeResolver;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.xd.dirt.integration.bus.MessageBusSupport.JavaClassMimeTypeConversion;
import org.springframework.xd.dirt.integration.bus.serializer.AbstractCodec;
import org.springframework.xd.dirt.integration.bus.serializer.CompositeCodec;
import org.springframework.xd.dirt.integration.bus.serializer.kryo.PojoCodec;
import org.springframework.xd.dirt.integration.bus.serializer.kryo.TupleCodec;
import org.springframework.xd.tuple.DefaultTuple;
import org.springframework.xd.tuple.Tuple;
import org.springframework.xd.tuple.TupleBuilder;

/**
 * @author Gary Russell
 * @author David Turanski
 */
public class MessageBusSupportTests {

	private ContentTypeResolver contentTypeResolver = new StringConvertingContentTypeResolver();

	private final TestMessageBus messageBus = new TestMessageBus();

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Before
	public void setUp() {
		Map<Class<?>, AbstractCodec<?>> codecs = new HashMap<Class<?>, AbstractCodec<?>>();
		codecs.put(Tuple.class, new TupleCodec());
		messageBus.setCodec(new CompositeCodec(codecs, new PojoCodec()));
	}

	@Test
	public void testBytesPassThru() {
		byte[] payload = "foo".getBytes();
		Message<byte[]> message = MessageBuilder.withPayload(payload).build();
		Message<?> converted = messageBus.serializePayloadIfNecessary(message,
				MimeTypeUtils.APPLICATION_OCTET_STREAM);
		assertSame(payload, converted.getPayload());
		assertEquals(MimeTypeUtils.APPLICATION_OCTET_STREAM,
				contentTypeResolver.resolve(converted.getHeaders()));
		Message<?> reconstructed = messageBus.deserializePayloadIfNecessary(converted);
		payload = (byte[]) reconstructed.getPayload();
		assertSame(converted.getPayload(), payload);
		assertNull(reconstructed.getHeaders().get(MessageBusSupport.ORIGINAL_CONTENT_TYPE_HEADER));
	}

	@Test
	public void testBytesPassThruContentType() {
		byte[] payload = "foo".getBytes();
		Message<byte[]> message = MessageBuilder.withPayload(payload)
				.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE)
				.build();
		Message<?> converted = messageBus.serializePayloadIfNecessary(message,
				MimeTypeUtils.APPLICATION_OCTET_STREAM);
		assertSame(payload, converted.getPayload());
		assertEquals(MimeTypeUtils.APPLICATION_OCTET_STREAM,
				contentTypeResolver.resolve(converted.getHeaders()));
		Message<?> reconstructed = messageBus.deserializePayloadIfNecessary(converted);
		payload = (byte[]) reconstructed.getPayload();
		assertSame(converted.getPayload(), payload);
		assertEquals(MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE,
				reconstructed.getHeaders().get(MessageHeaders.CONTENT_TYPE));
		assertNull(reconstructed.getHeaders().get(MessageBusSupport.ORIGINAL_CONTENT_TYPE_HEADER));
	}

	@Test
	public void testString() throws IOException {
		Message<?> converted = messageBus.serializePayloadIfNecessary(
				new GenericMessage<String>("foo"), MimeTypeUtils.APPLICATION_OCTET_STREAM);

		assertEquals(MimeTypeUtils.TEXT_PLAIN,
				contentTypeResolver.resolve(converted.getHeaders()));
		Message<?> reconstructed = messageBus.deserializePayloadIfNecessary(converted);
		assertEquals("foo", reconstructed.getPayload());
		assertNull(reconstructed.getHeaders().get(MessageHeaders.CONTENT_TYPE));
	}

	@Test
	public void testContentTypePreserved() throws IOException {
		Message<String> inbound = MessageBuilder.withPayload("{\"foo\":\"foo\"}")
				.copyHeaders(Collections.singletonMap(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON))
				.build();
		Message<?> converted = messageBus.serializePayloadIfNecessary(
				inbound, MimeTypeUtils.APPLICATION_OCTET_STREAM);

		assertEquals(MimeTypeUtils.TEXT_PLAIN,
				contentTypeResolver.resolve(converted.getHeaders()));
		assertEquals(MimeTypeUtils.APPLICATION_JSON,
				converted.getHeaders().get(MessageBusSupport.ORIGINAL_CONTENT_TYPE_HEADER));
		Message<?> reconstructed = messageBus.deserializePayloadIfNecessary(converted);
		assertEquals("{\"foo\":\"foo\"}", reconstructed.getPayload());
		assertEquals(MimeTypeUtils.APPLICATION_JSON, reconstructed.getHeaders().get(MessageHeaders.CONTENT_TYPE));
	}

	@Test
	public void testPojoSerialization() {
		Message<?> converted = messageBus.serializePayloadIfNecessary(
				new GenericMessage<Foo>(new Foo("bar")),
				MimeTypeUtils.APPLICATION_OCTET_STREAM);
		MimeType mimeType = contentTypeResolver.resolve(converted.getHeaders());
		assertEquals("application", mimeType.getType());
		assertEquals("x-java-object", mimeType.getSubtype());
		assertEquals(Foo.class.getName(), mimeType.getParameter("type"));

		Message<?> reconstructed = messageBus.deserializePayloadIfNecessary(converted);
		assertEquals("bar", ((Foo) reconstructed.getPayload()).getBar());
		assertNull(reconstructed.getHeaders().get(MessageHeaders.CONTENT_TYPE));
	}

	@Test
	public void testPojoWithXJavaObjectMimeTypeNoType() {
		Message<?> converted = messageBus.serializePayloadIfNecessary(
				new GenericMessage<Foo>(new Foo("bar")),
				MimeTypeUtils.APPLICATION_OCTET_STREAM);
		MimeType mimeType = contentTypeResolver.resolve(converted.getHeaders());
		assertEquals("application", mimeType.getType());
		assertEquals("x-java-object", mimeType.getSubtype());
		assertEquals(Foo.class.getName(), mimeType.getParameter("type"));

		Message<?> reconstructed = messageBus.deserializePayloadIfNecessary(converted);
		assertEquals("bar", ((Foo) reconstructed.getPayload()).getBar());
		assertNull(reconstructed.getHeaders().get(MessageHeaders.CONTENT_TYPE));
	}

	@Test
	public void testPojoWithXJavaObjectMimeTypeExplicitType() {
		Message<?> converted = messageBus.serializePayloadIfNecessary(
				new GenericMessage<Foo>(new Foo("bar")),
				MimeTypeUtils.APPLICATION_OCTET_STREAM);
		MimeType mimeType = contentTypeResolver.resolve(converted.getHeaders());
		assertEquals("application", mimeType.getType());
		assertEquals("x-java-object", mimeType.getSubtype());
		assertEquals(Foo.class.getName(), mimeType.getParameter("type"));

		Message<?> reconstructed = messageBus.deserializePayloadIfNecessary(converted);
		assertEquals("bar", ((Foo) reconstructed.getPayload()).getBar());
		assertNull(reconstructed.getHeaders().get(MessageHeaders.CONTENT_TYPE));
	}

	@Test
	public void testTupleSerialization() {
		Tuple payload = TupleBuilder.tuple().of("foo", "bar");
		Message<?> converted = messageBus.serializePayloadIfNecessary(new GenericMessage<Tuple>(payload),
				MimeTypeUtils.APPLICATION_OCTET_STREAM);
		MimeType mimeType = contentTypeResolver.resolve(converted.getHeaders());
		assertEquals("application", mimeType.getType());
		assertEquals("x-java-object", mimeType.getSubtype());
		assertEquals(DefaultTuple.class.getName(), mimeType.getParameter("type"));

		Message<?> reconstructed = messageBus.deserializePayloadIfNecessary(converted);
		assertEquals("bar", ((Tuple) reconstructed.getPayload()).getString("foo"));
		assertNull(reconstructed.getHeaders().get(MessageHeaders.CONTENT_TYPE));
	}

	@Test
	public void mimeTypeIsSimpleObject() throws ClassNotFoundException {
		MimeType mt = JavaClassMimeTypeConversion.mimeTypeFromObject(new Object());
		String className = JavaClassMimeTypeConversion.classNameFromMimeType(mt);
		assertEquals(Object.class, Class.forName(className));
	}

	@Test
	public void mimeTypeIsObjectArray() throws ClassNotFoundException {
		MimeType mt = JavaClassMimeTypeConversion.mimeTypeFromObject(new String[0]);
		String className = JavaClassMimeTypeConversion.classNameFromMimeType(mt);
		assertEquals(String[].class, Class.forName(className));
	}

	@Test
	public void mimeTypeIsMultiDimensionalObjectArray() throws ClassNotFoundException {
		MimeType mt = JavaClassMimeTypeConversion.mimeTypeFromObject(new String[0][0][0]);
		String className = JavaClassMimeTypeConversion.classNameFromMimeType(mt);
		assertEquals(String[][][].class, Class.forName(className));
	}

	@Test
	public void mimeTypeIsPrimitiveArray() throws ClassNotFoundException {
		MimeType mt = JavaClassMimeTypeConversion.mimeTypeFromObject(new int[0]);
		String className = JavaClassMimeTypeConversion.classNameFromMimeType(mt);
		assertEquals(int[].class, Class.forName(className));
	}

	@Test
	public void mimeTypeIsMultiDimensionalPrimitiveArray() throws ClassNotFoundException {
		MimeType mt = JavaClassMimeTypeConversion.mimeTypeFromObject(new int[0][0][0]);
		String className = JavaClassMimeTypeConversion.classNameFromMimeType(mt);
		assertEquals(int[][][].class, Class.forName(className));
	}

	public static class Foo {

		private String bar;

		public Foo() {
		}

		public Foo(String bar) {
			this.bar = bar;
		}

		public String getBar() {
			return bar;
		}

		public void setBar(String bar) {
			this.bar = bar;
		}

	}

	public static class Bar {

		private String foo;

		public Bar() {
		}

		public Bar(String foo) {
			this.foo = foo;
		}

		public String getFoo() {
			return foo;
		}

		public void setFoo(String foo) {
			this.foo = foo;
		}

	}

	public class TestMessageBus extends MessageBusSupport {

		@Override
		public void bindConsumer(String name, MessageChannel channel, Properties properties) {
		}

		@Override
		public void bindPubSubConsumer(String name, MessageChannel moduleInputChannel,
				Properties properties) {
		}

		@Override
		public void bindPubSubProducer(String name, MessageChannel moduleOutputChannel,
				Properties properties) {
		}

		@Override
		public void bindProducer(String name, MessageChannel channel, Properties properties) {
		}

		@Override
		public void bindRequestor(String name, MessageChannel requests, MessageChannel replies,
				Properties properties) {
		}

		@Override
		public void bindReplier(String name, MessageChannel requests, MessageChannel replies,
				Properties properties) {
		}

	}

}
