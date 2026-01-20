package org.thoughtcrime.securesms.breeze;

import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.mms.OutgoingMessage;

public class DraftManager {

  private static final DraftManager INSTANCE = new DraftManager();

  private OutgoingMessage pendingDraft;
  private long pendingThreadId;

  private DraftManager() {}

  public static DraftManager getInstance() {
    return INSTANCE;
  }

  public void setPendingDraft(OutgoingMessage message, long threadId) {
    this.pendingDraft = message;
    this.pendingThreadId = threadId;
  }

  public @Nullable OutgoingMessage getPendingDraft() {
    return pendingDraft;
  }

  public long getPendingThreadId() {
    return pendingThreadId;
  }

  public void clearDraft() {
    this.pendingDraft = null;
    this.pendingThreadId = -1;
  }

  public boolean hasPendingDraft() {
    return pendingDraft != null;
  }
}
