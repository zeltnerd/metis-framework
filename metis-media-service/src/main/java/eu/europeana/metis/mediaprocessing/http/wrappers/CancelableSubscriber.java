package eu.europeana.metis.mediaprocessing.http.wrappers;

import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CancelableSubscriber<T> implements BodySubscriber<T> {

  private static final Logger LOG = LoggerFactory.getLogger(CancelableSubscriber.class);

  private final CountDownLatch latch;
  private final BodySubscriber<T> subscriber;
  private Subscription subscription;
  private boolean isCancelled;

  CancelableSubscriber(BodySubscriber<T> subscriber, CountDownLatch latch) {
    this.subscriber = subscriber;
    this.latch = latch;
    isCancelled = false;
  }

  @Override
  public CompletionStage<T> getBody() {
    return subscriber.getBody();
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    subscriber.onSubscribe(subscription);
    this.subscription = subscription;
    latch.countDown();
  }

  @Override
  public void onNext(List<ByteBuffer> item) {
    subscriber.onNext(item);
  }

  @Override
  public void onError(Throwable throwable) {
    subscriber.onError(throwable);
  }

  @Override
  public void onComplete() {
    subscriber.onComplete();
  }

  public void cancel() {
    subscription.cancel();
    isCancelled = true;
    LOG.debug("Subscription got cancelled");
  }

  public boolean isCancelled(){
    return isCancelled;
  }
}


