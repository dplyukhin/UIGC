package edu.illinois.osl.akka.gc.protocols.monotone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.illinois.osl.akka.gc.interfaces.Pretty;

class State implements Pretty {

  static int ARRAY_MAX = 16;

  /** A sequence number used for generating unique tokens */
  int count;
  /** Where in the array to insert the next "created" refob */
  int createdIdx;
  /** Where in the array to insert the next "updated" refob */
  int updatedIdx;
  /** This actor's ref to itself */
  Object selfRef;
  /** Tracks references created by this actor */
  Refob<?>[] created;
  /** Tracks all the refs that have been updated in this entry period */
  Refob<?>[] updated;
  /** Tracks how many messages are received using each reference. */
  Map<Token, Integer> recvCount;

  public State() {
    this.count = 0;
    this.createdIdx = 0;
    this.created = new Refob<?>[ARRAY_MAX];
    this.updated = new Refob<?>[ARRAY_MAX];
    this.recvCount = new HashMap<>();
  }

  public void onCreate(Refob<?> ref) {
    created[createdIdx++] = ref;
    if (createdIdx >= ARRAY_MAX) {
        finalizeEntry();
    }
  }

  public void onReceive(Refob<?> ref) {
    ref.initialize(this);
    ref.hasChangedThisPeriod_$eq(true);
    updated[updatedIdx++] = ref;
    if (updatedIdx >= ARRAY_MAX) {
        finalizeEntry();
    }
  }

  public void onDeactivate(Refob<?> ref) {
    ref.info_$eq(RefobInfo.deactivate(ref.info()));
    if (!ref.hasChangedThisPeriod()) {
        ref.hasChangedThisPeriod_$eq(true);
        updated[updatedIdx++] = ref;
        if (updatedIdx >= ARRAY_MAX) {
            finalizeEntry();
        }
    }
  }

  public void onSend(Refob<?> ref) {
    ref.info_$eq(RefobInfo.incSendCount(ref.info()));
    if (!ref.hasChangedThisPeriod()) {
        ref.hasChangedThisPeriod_$eq(true);
        updated[updatedIdx++] = ref;
        if (updatedIdx >= ARRAY_MAX) {
            finalizeEntry();
        }
    }
  }

  public void incReceiveCount(Token token) {
    Integer count = recvCount.getOrDefault(token, 0);
    recvCount.put(token, count + 1);
    if (recvCount.size() >= ARRAY_MAX) {
        finalizeEntry();
    }
  }

  public void finalizeEntry() {
    Object[] _created = null;
    if (createdIdx > 0) {
        _created = new Refob<?>[createdIdx];
        for (int i = 0; i < createdIdx; i++) {
            _created[i] = this.created[i];
            this.created[i] = null;
        }
        createdIdx = 0;
    }

    Map<Token, Integer> _recvCount = null; 
    if (!recvCount.isEmpty()) {
        _recvCount = this.recvCount;
        this.recvCount = new HashMap<>();
    }

    short[] refInfos = null; 
    if (updatedIdx > 0) {
        refInfos = new short[updatedIdx];
        for (int i = 0; i < updatedIdx; i++) {
            refInfos[i] = this.updated[i].info();
            this.updated[i].resetInfo();
            updated[i] = null;
        }
        updatedIdx = 0;
    }
  }

  @Override
  public String pretty() {
    return String.format("[TODO: Implement Monotone.State.pretty]");
  }
}
