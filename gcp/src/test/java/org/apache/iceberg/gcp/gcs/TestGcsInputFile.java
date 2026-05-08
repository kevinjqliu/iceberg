/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.gcp.gcs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.google.cloud.gcs.analyticscore.client.GcsFileSystem;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import java.io.IOException;
import org.apache.iceberg.gcp.GCPProperties;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class TestGcsInputFile {

  private static final String TEST_BUCKET = "TEST_BUCKET";
  private static final String KEY = "file/path/a.dat";
  private static final String LOCATION = "gs://" + TEST_BUCKET + "/" + KEY;
  private static final long FILE_SIZE = 1024L;

  private Storage storage;
  private GcsFileSystem gcsFileSystem;
  private PrefixedStorage prefixedStorage;
  private GCPProperties gcpProperties;
  private MetricsContext metricsContext;
  private Blob blob;

  @BeforeEach
  public void before() {
    storage = mock(Storage.class);
    gcsFileSystem = mock(GcsFileSystem.class);
    prefixedStorage = mock(PrefixedStorage.class);
    gcpProperties = new GCPProperties();
    metricsContext = MetricsContext.nullMetrics();
    blob = mock(Blob.class);
    when(prefixedStorage.storage()).thenReturn(storage);
    when(prefixedStorage.gcsFileSystem()).thenReturn(gcsFileSystem);
    when(prefixedStorage.gcpProperties()).thenReturn(gcpProperties);
    when(storage.get(any(BlobId.class))).thenReturn(blob);
    when(blob.getSize()).thenReturn(FILE_SIZE);
  }

  @Test
  public void fromLocation() {
    GCSInputFile inputFile = GCSInputFile.fromLocation(LOCATION, prefixedStorage, metricsContext);

    assertThat(inputFile.blobId()).isEqualTo(BlobId.fromGsUtilUri(LOCATION));
    assertThat(inputFile.getLength()).isEqualTo(FILE_SIZE);
  }

  @Test
  public void fromLocationWithLength() {
    GCSInputFile inputFile =
        GCSInputFile.fromLocation(LOCATION, FILE_SIZE, prefixedStorage, metricsContext);

    assertThat(inputFile.blobId()).isEqualTo(BlobId.fromGsUtilUri(LOCATION));
    assertThat(inputFile.getLength()).isEqualTo(FILE_SIZE);
  }

  @Test
  public void getLength() {
    when(blob.getSize()).thenReturn(FILE_SIZE);
    GCSInputFile inputFile =
        new GCSInputFile(
            storage,
            prefixedStorage,
            BlobId.fromGsUtilUri(LOCATION),
            null,
            gcpProperties,
            metricsContext);

    assertThat(inputFile.getLength()).isEqualTo(FILE_SIZE);
  }

  @Test
  public void getLengthCached() {
    GCSInputFile inputFile =
        new GCSInputFile(
            storage,
            prefixedStorage,
            BlobId.fromGsUtilUri(LOCATION),
            FILE_SIZE,
            gcpProperties,
            metricsContext);

    assertThat(inputFile.getLength()).isEqualTo(FILE_SIZE);
  }

  @Test
  public void newStreamGcsAnalyticsCoreEnabled() throws IOException {
    GCPProperties enabledGcpProperties =
        new GCPProperties(ImmutableMap.of(GCPProperties.GCS_ANALYTICS_CORE_ENABLED, "true"));
    BlobId blobId = BlobId.fromGsUtilUri(LOCATION);
    try (MockedStatic<GcsAnalyticsCoreHelper> mocked = mockStatic(GcsAnalyticsCoreHelper.class)) {
      SeekableInputStream mockStream = mock(SeekableInputStream.class);
      mocked
          .when(
              () ->
                  GcsAnalyticsCoreHelper.newInputStream(
                      eq(gcsFileSystem), eq(blobId), eq(FILE_SIZE), eq(metricsContext)))
          .thenReturn(mockStream);

      GCSInputFile inputFile =
          new GCSInputFile(
              storage,
              prefixedStorage,
              BlobId.fromGsUtilUri(LOCATION),
              FILE_SIZE,
              enabledGcpProperties,
              metricsContext);

      try (SeekableInputStream stream = inputFile.newStream()) {
        assertThat(stream).isSameAs(mockStream);
      }
    }
  }

  @Test
  public void newStreamGcsAnalyticsCoreEnabledObjectSizeNull() throws IOException {
    GCPProperties enabledGcpProperties =
        new GCPProperties(ImmutableMap.of(GCPProperties.GCS_ANALYTICS_CORE_ENABLED, "true"));
    BlobId blobId = BlobId.fromGsUtilUri(LOCATION);
    try (MockedStatic<GcsAnalyticsCoreHelper> mocked = mockStatic(GcsAnalyticsCoreHelper.class)) {
      SeekableInputStream mockStream = mock(SeekableInputStream.class);
      mocked
          .when(
              () ->
                  GcsAnalyticsCoreHelper.newInputStream(
                      eq(gcsFileSystem), eq(blobId), isNull(), eq(metricsContext)))
          .thenReturn(mockStream);

      GCSInputFile inputFile =
          new GCSInputFile(
              storage,
              prefixedStorage,
              BlobId.fromGsUtilUri(LOCATION),
              null,
              enabledGcpProperties,
              metricsContext);

      try (SeekableInputStream stream = inputFile.newStream()) {
        assertThat(stream).isSameAs(mockStream);
      }
    }
  }

  @Test
  public void newStreamGcsAnalyticsCoreDisabled() throws IOException {
    GCSInputFile inputFile =
        new GCSInputFile(
            storage,
            prefixedStorage,
            BlobId.fromGsUtilUri(LOCATION),
            FILE_SIZE,
            gcpProperties,
            metricsContext);

    try (MockedConstruction<GCSInputStream> mocked =
        mockConstruction(
            GCSInputStream.class,
            (mock, context) -> {
              assertThat(context.arguments()).hasSize(5);
              assertThat(context.arguments().get(0)).isEqualTo(storage);
              assertThat(context.arguments().get(1)).isEqualTo(BlobId.fromGsUtilUri(LOCATION));
              assertThat(context.arguments().get(2)).isEqualTo(FILE_SIZE);
              assertThat(context.arguments().get(3)).isEqualTo(gcpProperties);
              assertThat(context.arguments().get(4)).isEqualTo(metricsContext);
            })) {
      try (SeekableInputStream stream = inputFile.newStream()) {
        assertThat(stream).isInstanceOf(GCSInputStream.class);
        assertThat(mocked.constructed()).hasSize(1);
      }
    }
  }

  @Test
  public void newStreamAnalyticsCoreInitializationFailed() throws IOException {
    GCPProperties enabledGcpProperties =
        new GCPProperties(ImmutableMap.of(GCPProperties.GCS_ANALYTICS_CORE_ENABLED, "true"));
    BlobId blobId = BlobId.fromGsUtilUri(LOCATION);

    try (MockedStatic<GcsAnalyticsCoreHelper> mocked = mockStatic(GcsAnalyticsCoreHelper.class)) {
      mocked
          .when(
              () ->
                  GcsAnalyticsCoreHelper.newInputStream(
                      eq(gcsFileSystem), eq(blobId), isNull(), eq(metricsContext)))
          .thenThrow(new IOException("GCS connector failed"));

      GCSInputFile inputFile =
          new GCSInputFile(
              storage,
              prefixedStorage,
              BlobId.fromGsUtilUri(LOCATION),
              null,
              enabledGcpProperties,
              metricsContext);
      try (MockedConstruction<GCSInputStream> inputStreamMocked =
          mockConstruction(
              GCSInputStream.class,
              (mock, context) -> {
                assertThat(context.arguments()).hasSize(5);
                assertThat(context.arguments().get(0)).isEqualTo(storage);
                assertThat(context.arguments().get(1)).isEqualTo(BlobId.fromGsUtilUri(LOCATION));
                assertThat(context.arguments().get(2)).isEqualTo(null);
                assertThat(context.arguments().get(3)).isEqualTo(enabledGcpProperties);
                assertThat(context.arguments().get(4)).isEqualTo(metricsContext);
              })) {
        SeekableInputStream stream = inputFile.newStream();
        assertThat(stream).isInstanceOf(GCSInputStream.class);
        assertThat(inputStreamMocked.constructed()).hasSize(1);
        mocked.verify(
            () ->
                GcsAnalyticsCoreHelper.newInputStream(
                    eq(gcsFileSystem), eq(blobId), isNull(), eq(metricsContext)));
        stream.close();
      }
    }
  }
}
