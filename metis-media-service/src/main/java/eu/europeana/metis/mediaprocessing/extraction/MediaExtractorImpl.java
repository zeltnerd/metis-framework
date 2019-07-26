package eu.europeana.metis.mediaprocessing.extraction;

import eu.europeana.metis.mediaprocessing.MediaExtractor;
import eu.europeana.metis.mediaprocessing.exception.MediaExtractionException;
import eu.europeana.metis.mediaprocessing.exception.MediaProcessorException;
import eu.europeana.metis.mediaprocessing.http.ResourceDownloadClient;
import eu.europeana.metis.mediaprocessing.model.RdfResourceEntry;
import eu.europeana.metis.mediaprocessing.model.Resource;
import eu.europeana.metis.mediaprocessing.model.ResourceExtractionResult;
import eu.europeana.metis.utils.MediaType;
import java.io.IOException;
import java.util.Optional;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts technical metadata and generates thumbnails for web resources.
 */
public class MediaExtractorImpl implements MediaExtractor {

  private static final Logger LOGGER = LoggerFactory.getLogger(MediaExtractorImpl.class);

  private final ResourceDownloadClient resourceDownloadClient;
  private final Tika tika;

  private final ImageProcessor imageProcessor;
  private final AudioVideoProcessor audioVideoProcessor;
  private final TextProcessor textProcessor;

  /**
   * Constructor meant for testing purposes.
   *
   * @param resourceDownloadClient The download client for resources.
   * @param tika A tika instance.
   * @param imageProcessor An image processor.
   * @param audioVideoProcessor An audio/video processor.
   * @param textProcessor A text processor.
   */
  MediaExtractorImpl(ResourceDownloadClient resourceDownloadClient,
      Tika tika, ImageProcessor imageProcessor, AudioVideoProcessor audioVideoProcessor,
      TextProcessor textProcessor) {
    this.resourceDownloadClient = resourceDownloadClient;
    this.tika = tika;
    this.imageProcessor = imageProcessor;
    this.audioVideoProcessor = audioVideoProcessor;
    this.textProcessor = textProcessor;
  }

  /**
   * Constructor for non-testing purposes.
   *
   * @param redirectCount The maximum number of times we will follow a redirect.
   * @param thumbnailGenerateTimeout The maximum amount of time, in seconds, a thumbnail generation
   * command is allowed to take before it is forcibly destroyed (i.e. cancelled).
   * @param audioVideoProbeTimeout The maximum amount of time, in seconds, a audio/video probe
   * command is allowed to take before it is forcibly destroyed (i.e. cancelled).
   * @param connectTimeout The connection timeout in milliseconds for downloading resources.
   * @param socketTimeout The socket timeout in milliseconds for downloading resources.
   * @throws MediaProcessorException In case something went wrong while initializing the extractor.
   */
  public MediaExtractorImpl(int redirectCount, int thumbnailGenerateTimeout,
      int audioVideoProbeTimeout, int connectTimeout, int socketTimeout)
      throws MediaProcessorException {
    final ThumbnailGenerator thumbnailGenerator = new ThumbnailGenerator(
        new CommandExecutor(thumbnailGenerateTimeout));
    this.resourceDownloadClient = new ResourceDownloadClient(redirectCount,
        this::shouldDownloadForFullProcessing, connectTimeout, socketTimeout);
    this.tika = new Tika();
    this.imageProcessor = new ImageProcessor(thumbnailGenerator);
    this.audioVideoProcessor = new AudioVideoProcessor(new CommandExecutor(audioVideoProbeTimeout));
    this.textProcessor = new TextProcessor(thumbnailGenerator);
  }

  @Override
  public ResourceExtractionResult performMediaExtraction(RdfResourceEntry resourceEntry)
      throws MediaExtractionException {

    // Download resource and then perform media extraction on it.
    try (Resource resource = resourceDownloadClient.download(resourceEntry)) {
      return performFullProcessing(resource);
    } catch (IOException | RuntimeException e) {
      throw new MediaExtractionException(
          "Problem while processing " + resourceEntry.getResourceUrl(), e);
    }
  }

  String detectAndVerifyMimeType(Resource resource) throws MediaExtractionException {

    // Obtain the mime type. If no content, check against the URL. Note: we use the actual location
    // instead of the resource URL (because Tika doesn't seem to do forwarding properly).
    final String detectedMimeType;
    try {
      detectedMimeType = resource.hasContent() ? tika.detect(resource.getContentPath())
          : tika.detect(resource.getActualLocation().toURL());
    } catch (IOException e) {
      throw new MediaExtractionException("Mime type checking error", e);
    }

    // Log if the detected mime type is different from the provided one. If application/xhtml+xml is
    // detected from tika, and text/html is provided, we don't give a warning.
    final String providedMimeType = resource.getProvidedMimeType();
    if (!("application/xhtml+xml".equals(detectedMimeType) && providedMimeType
        .startsWith("text/html")) && !detectedMimeType.equals(providedMimeType)) {
      LOGGER.info("Invalid mime type provided (should be {}, was {}): {}", detectedMimeType,
          providedMimeType, resource.getResourceUrl());
    }

    // Verify that we have content when we need to.
    try {
      if (!resource.hasContent() && shouldDownloadForFullProcessing(detectedMimeType)) {
        throw new MediaExtractionException(
            "File content is not downloaded and mimeType does not support processing without a downloaded file.");
      }
    } catch (IOException e) {
      throw new MediaExtractionException("Could not determine whether resource has content.", e);
    }

    // Done
    return detectedMimeType;
  }

  MediaProcessor chooseMediaProcessor(MediaType mediaType){
    final MediaProcessor processor;
    switch (mediaType) {
      case TEXT:
        processor = textProcessor;
        break;
      case AUDIO:
      case VIDEO:
        processor = audioVideoProcessor;
        break;
      case IMAGE:
        processor = imageProcessor;
        break;
      default:
        processor = null;
        break;
    }
    return processor;
  }

  ResourceExtractionResult performFullProcessing(Resource resource) throws MediaExtractionException {

    // Detect and verify the mime type
    final String detectedMimeType = detectAndVerifyMimeType(resource);

    // Choose the right media processor.
    final MediaProcessor processor = chooseMediaProcessor(MediaType.getMediaType(detectedMimeType));

    // Process the resource.
    return processor == null ? null : processor.process(resource, detectedMimeType);
  }

  @Override
  public void close() throws IOException {
    resourceDownloadClient.close();
  }

  /**
   * @return true if and only if resources of the given type need to be downloaded before performing
   * full processing.
   */
  boolean shouldDownloadForFullProcessing(String mimeType) {
    return Optional.of(MediaType.getMediaType(mimeType)).map(this::chooseMediaProcessor)
        .map(MediaProcessor::downloadResourceForFullProcessing).orElse(false);
  }
}
