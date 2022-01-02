/*
 * Copyright 2019-2022 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cloudnetservice.modules.s3;

import eu.cloudnetservice.cloudnet.common.function.ThrowableConsumer;
import eu.cloudnetservice.cloudnet.common.io.FileUtils;
import eu.cloudnetservice.cloudnet.common.log.LogManager;
import eu.cloudnetservice.cloudnet.common.log.Logger;
import eu.cloudnetservice.cloudnet.common.stream.ListeningOutputStream;
import eu.cloudnetservice.cloudnet.driver.service.ServiceTemplate;
import eu.cloudnetservice.cloudnet.driver.template.FileInfo;
import eu.cloudnetservice.cloudnet.driver.template.TemplateStorage;
import eu.cloudnetservice.modules.s3.config.S3TemplateStorageConfig;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

public class S3TemplateStorage implements TemplateStorage {

  private static final Logger LOGGER = LogManager.logger(S3TemplateStorage.class);

  private final S3Client client;
  private final S3TemplateStorageModule module;

  public S3TemplateStorage(@NonNull S3TemplateStorageModule module) {
    this.module = module;
    this.client = S3Client.builder()
      .region(Region.of(this.config().region()))
      .endpointOverride(this.config().endpointOverride())
      .dualstackEnabled(this.config().dualstackEndpointEnabled())
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
        this.config().accessKey(),
        this.config().secretKey())))
      .build();

    // init the bucket
    try {
      this.client.headBucket(HeadBucketRequest.builder().bucket(this.config().bucket()).build());
    } catch (NoSuchBucketException exception) {
      // try to create the bucket
      try {
        this.client.createBucket(CreateBucketRequest.builder().bucket(this.config().bucket()).build());
      } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException ignored) {
        // unlikely to happen - not an error
      }
    }
  }

  @Override
  public @NonNull String name() {
    return this.config().name();
  }

  @Override
  public boolean deployDirectory(
    @NonNull Path directory,
    @NonNull ServiceTemplate target,
    @Nullable Predicate<Path> fileFilter
  ) {
    var result = new AtomicBoolean(true);
    // walk down the file tree
    FileUtils.walkFileTree(directory, ($, file) -> {
      if (!Files.isDirectory(file)) {
        try {
          var request = PutObjectRequest.builder()
            .bucket(this.config().bucket())
            .key(this.getBucketPath(target, directory, file))
            .contentType(this.getContentType(file))
            .contentLength(Files.size(file))
            .build();
          this.client.putObject(request, RequestBody.fromFile(file));
        } catch (Exception exception) {
          LOGGER.severe("Exception putting file %s into s3 bucket %s",
            exception,
            file.toAbsolutePath(),
            this.config().bucket());
          result.set(false);
        }
      }
    }, true, fileFilter == null ? path -> true : fileFilter::test);
    return result.get();
  }

  @Override
  public boolean deploy(
    @NonNull InputStream inputStream,
    @NonNull ServiceTemplate target
  ) {
    var temp = FileUtils.extract(inputStream, FileUtils.createTempFile());
    if (temp != null) {
      try {
        return this.deployDirectory(temp, target, null);
      } finally {
        FileUtils.delete(temp);
      }
    }
    return false;
  }

  @Override
  public boolean copy(@NonNull ServiceTemplate template, @NonNull Path directory) {
    try {
      // get the repo path
      var templatePath = this.getBucketPath(template);
      // list all files
      return this.listAllObjects(templatePath, null, content -> {
        // filter the content key
        var target = directory.resolve(content.key().substring(templatePath.length() + 1));
        FileUtils.createDirectory(target.getParent());
        // get the file
        var req = GetObjectRequest.builder()
          .key(content.key())
          .bucket(this.config().bucket())
          .build();
        try (InputStream stream = this.client.getObject(req); var out = Files.newOutputStream(target)) {
          FileUtils.copy(stream, out);
        }
      });
    } catch (Exception exception) {
      LOGGER.severe("Exception requesting object list from bucket for downloading", exception);
      return false;
    }
  }

  @Override
  public @Nullable InputStream zipTemplate(@NonNull ServiceTemplate template) {
    var localTarget = FileUtils.createTempFile();
    if (this.copy(template, localTarget)) {
      return FileUtils.zipToStream(localTarget);
    } else {
      return null;
    }
  }

  @Override
  public boolean delete(@NonNull ServiceTemplate template) {
    // get the contents we want to delete
    Set<ObjectIdentifier> toDelete = new HashSet<>();
    this.listAllObjects(
      this.getBucketPath(template),
      null,
      object -> toDelete.add(ObjectIdentifier.builder().key(object.key()).build()));

    try {
      // build the delete request
      var deleteRequest = DeleteObjectsRequest.builder()
        .bucket(this.config().bucket())
        .delete(Delete.builder().quiet(true).objects(toDelete).build())
        .build();
      this.client.deleteObjects(deleteRequest);
      // success
      return true;
    } catch (Exception exception) {
      LOGGER.severe("Exception deleting files template files", exception);
      return false;
    }
  }

  @Override
  public boolean create(@NonNull ServiceTemplate template) {
    return true; // there are no directories
  }

  @Override
  public boolean has(@NonNull ServiceTemplate template) {
    try {
      // check if we can get at least one object
      var request = ListObjectsV2Request.builder()
        .maxKeys(1)
        .fetchOwner(false)
        .bucket(this.config().bucket())
        .prefix(this.getBucketPath(template))
        .build();
      return !this.client.listObjectsV2(request).contents().isEmpty();
    } catch (Exception exception) {
      return false;
    }
  }

  @Override
  public @Nullable OutputStream appendOutputStream(
    @NonNull ServiceTemplate template,
    @NonNull String path
  ) throws IOException {
    ByteArrayOutputStream original;
    // try to get the old data
    try {
      var request = GetObjectRequest.builder()
        .bucket(this.config().bucket())
        .key(this.getBucketPath(template, path))
        .build();
      try (InputStream inputStream = this.client.getObject(request)) {
        original = new ByteArrayOutputStream(inputStream.available());
        inputStream.transferTo(original);
      }
    } catch (NoSuchKeyException exception) {
      original = new ByteArrayOutputStream();
    }
    // create a wrapped stream that replaced the file at the path when closing
    return this.wrapStream(template, path, original);
  }

  @Override
  public @Nullable OutputStream newOutputStream(@NonNull ServiceTemplate template, @NonNull String path) {
    return this.wrapStream(template, path, new ByteArrayOutputStream());
  }

  protected @NonNull OutputStream wrapStream(
    @NonNull ServiceTemplate template,
    @NonNull String filePath,
    @NonNull ByteArrayOutputStream original
  ) {
    return new ListeningOutputStream<>(original, stream -> {
      var content = stream.toByteArray();
      try (InputStream inputStream = new ByteArrayInputStream(content)) {
        var request = PutObjectRequest.builder()
          .bucket(this.config().bucket())
          .key(this.getBucketPath(template, filePath))
          .contentLength((long) content.length)
          .contentType("application/octet-stream")
          .build();
        this.client.putObject(request, RequestBody.fromInputStream(inputStream, content.length));
      }
    });
  }

  @Override
  public boolean createFile(@NonNull ServiceTemplate template, @NonNull String path) {
    try {
      var request = PutObjectRequest.builder()
        .bucket(this.getBucketPath(template, path))
        .key(this.getBucketPath(template, path))
        .contentLength(0L)
        .contentType("text/plain")
        .build();
      this.client.putObject(request, RequestBody.fromBytes(new byte[0]));
      // success
      return true;
    } catch (Exception exception) {
      return false;
    }
  }

  @Override
  public boolean createDirectory(@NonNull ServiceTemplate template, @NonNull String path) {
    return true; // there are no folders
  }

  @Override
  public boolean hasFile(@NonNull ServiceTemplate template, @NonNull String path) throws IOException {
    try {
      var request = GetObjectRequest.builder()
        .bucket(this.config().bucket())
        .key(this.getBucketPath(template, path))
        .build();
      this.client.getObject(request).close();
      // the file was present
      return true;
    } catch (NoSuchKeyException exception) {
      return false;
    }
  }

  @Override
  public boolean deleteFile(@NonNull ServiceTemplate template, @NonNull String path) {
    try {
      var request = DeleteObjectRequest.builder()
        .bucket(this.config().bucket())
        .key(this.getBucketPath(template, path))
        .build();
      this.client.deleteObject(request);
      // success
      return true;
    } catch (Exception exception) {
      return false;
    }
  }

  @Override
  public @Nullable InputStream newInputStream(@NonNull ServiceTemplate template, @NonNull String path) {
    try {
      var request = GetObjectRequest.builder()
        .bucket(this.config().bucket())
        .key(this.getBucketPath(template, path))
        .build();
      return this.client.getObject(request);
    } catch (NoSuchKeyException exception) {
      return null;
    }
  }

  @Override
  public @Nullable FileInfo fileInfo(@NonNull ServiceTemplate template, @NonNull String path) {
    try {
      var bucketPath = this.getBucketPath(template, path);
      // get the object info
      var request = HeadObjectRequest.builder()
        .bucket(this.config().bucket())
        .key(bucketPath)
        .build();
      var response = this.client.headObject(request);
      // convert to a file info
      var parts = bucketPath.split("/");
      return new FileInfo(
        path,
        parts[parts.length - 1],
        false, // there are no directories
        false,
        // creating & updating are the same thing - accesses are not given by the api
        response.lastModified().toEpochMilli(),
        response.lastModified().toEpochMilli(),
        response.lastModified().toEpochMilli(),
        response.contentLength());
    } catch (NoSuchKeyException exception) {
      return null;
    }
  }

  @Override
  public @Nullable FileInfo[] listFiles(@NonNull ServiceTemplate template, @NonNull String dir, boolean deep) {
    // get the initial data we need to strip off
    var initialStrip = this.getBucketPath(template).length();
    // collect all files
    Collection<FileInfo> files = new ArrayList<>();
    this.listAllObjects(this.getBucketPath(template, dir), null, object -> {
      var parts = object.key().split("/");
      files.add(new FileInfo(
        object.key().substring(initialStrip),
        parts[parts.length - 1],
        false, // there are no directories
        false,
        object.lastModified().toEpochMilli(),
        object.lastModified().toEpochMilli(),
        object.lastModified().toEpochMilli(),
        object.size()));
    });
    // finish the collection
    return files.toArray(new FileInfo[0]);
  }

  @Override
  public @NonNull Collection<ServiceTemplate> templates() {
    Set<ServiceTemplate> result = new HashSet<>();
    // list all files - filter out the possible template prefixes
    this.listAllObjects("", null, object -> {
      var parts = object.key().split("/");
      if (parts.length >= 2) {
        result.add(ServiceTemplate.builder()
          .storage(this.config().name())
          .prefix(parts[0])
          .name(parts[1])
          .build());
      }
    });
    return result;
  }

  @Override
  public void close() {
    this.client.close();
  }

  protected boolean listAllObjects(
    @NonNull String prefix,
    @Nullable String marker,
    @NonNull ThrowableConsumer<S3Object, Exception> handler
  ) {
    try {
      var response = this.client.listObjectsV2(ListObjectsV2Request.builder()
        .prefix(prefix)
        .fetchOwner(false)
        .continuationToken(marker)
        .bucket(this.config().bucket())
        .build());
      // handle all results
      for (var content : response.contents()) {
        handler.accept(content);
      }
      // check if there is a need to continue
      if (response.isTruncated() && response.continuationToken() != null) {
        return this.listAllObjects(prefix, response.continuationToken(), handler);
      } else {
        // no need to continue - success!
        return true;
      }
    } catch (Exception exception) {
      LOGGER.severe("Exception listing content of bucket %s with prefix %s",
        exception,
        this.config().bucket(),
        prefix);
      return false;
    }
  }

  protected @NonNull String getContentType(@NonNull Path file) {
    try {
      return Files.probeContentType(file);
    } catch (IOException exception) {
      return "application/octet-stream";
    }
  }

  protected @NonNull String getBucketPath(@NonNull ServiceTemplate template) {
    return String.format("%s/%s", template.prefix(), template.name());
  }

  protected @NonNull String getBucketPath(@NonNull ServiceTemplate template, @NonNull String subPath) {
    return String.format("%s/%s", this.getBucketPath(template), subPath.replace('\\', '/'));
  }

  protected @NonNull String getBucketPath(@NonNull ServiceTemplate template, @NonNull Path root, @NonNull Path file) {
    return this.getBucketPath(template, root.relativize(file).toString());
  }

  protected @NonNull S3TemplateStorageConfig config() {
    return this.module.config();
  }
}