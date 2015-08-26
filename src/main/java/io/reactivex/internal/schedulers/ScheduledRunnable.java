/**
 * Copyright 2015 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.internal.schedulers;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceArray;

import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.*;

public final class ScheduledRunnable extends AtomicReferenceArray<Object> implements Runnable, Disposable {
    /** */
    private static final long serialVersionUID = -6120223772001106981L;
    final Runnable actual;
    
    static final Object DISPOSED = new Object();
    
    static final Object DONE = new Object();
    
    public ScheduledRunnable(Runnable actual, CompositeResource<Disposable> parent) {
        super(2);
        this.actual = actual;
        this.lazySet(0, parent);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        try {
            actual.run();
        } catch (Throwable e) {
            // TODO error management with plugins, etc.
            e.printStackTrace();
        } finally {
            Object o = get(0);
            if (o != DISPOSED) {
                // done races with dispose here
                if (compareAndSet(0, o, DONE)) {
                    ((CompositeResource<Disposable>)o).delete(this);
                }
            }
            
            for (;;) {
                o = get(1);
                if (o != DISPOSED) {
                    // o is either null or a future
                    if (compareAndSet(1, o, DONE)) {
                        break;
                    }
                }
            }
        }
    }
    
    public void setFuture(Future<?> f) {
        for (;;) {
            Object o = get(1);
            if (o == DONE) {
                return;
            }
            if (o == DISPOSED) {
                f.cancel(true);
                return;
            }
            if (compareAndSet(1, o, f)) {
                return;
            }
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void dispose() {
        for (;;) {
            Object o = get(1);
            if (o == DONE || o == DISPOSED) {
                break;
            }
            if (compareAndSet(1, o, DISPOSED)) {
                if (o != null) {
                    ((Future<?>)o).cancel(true);
                }
                break;
            }
        }
        
        for (;;) {
            Object o = get(0);
            if (o == DONE || o == DISPOSED) {
                break;
            }
            if (compareAndSet(1, o, DISPOSED)) {
                ((CompositeResource<Disposable>)o).delete(this);
            }
        }
    }
}
