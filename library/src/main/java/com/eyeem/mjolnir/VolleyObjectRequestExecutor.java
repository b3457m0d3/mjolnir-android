package com.eyeem.mjolnir;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;

/**
 * Created by vishna on 16/11/13.
 */
public class VolleyObjectRequestExecutor {
   RequestBuilder requestBuilder;
   Response.Listener<Object> listener;
   Response.ErrorListener errorListener;
   Class objectClass;

   public VolleyObjectRequestExecutor(com.eyeem.mjolnir.RequestBuilder requestBuilder, Class objectClass) {
      this.requestBuilder = requestBuilder.sign();
      this.objectClass = objectClass;
   }

   public VolleyObjectRequestExecutor listener(Response.Listener<Object> listener) {
      this.listener = listener;
      return this;
   }

   public VolleyObjectRequestExecutor errorListener(Response.ErrorListener errorListener) {
      this.errorListener = errorListener;
      return this;
   }

   public Request build() {
      return new ObjectRequest(requestBuilder, objectClass, listener, errorListener);
   }

   public void enqueue(RequestQueue queue) {
      queue.add(build());
   }
}
