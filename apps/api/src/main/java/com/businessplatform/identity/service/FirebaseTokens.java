package com.businessplatform.identity.service;

/** Checks a Firebase Authentication ID token and says who it is for (tests replace it). */
public interface FirebaseTokens {

    /** Who a valid token belongs to; {@code null} name or picture when Google didn't give one. */
    record Identity(String uid, String email, boolean emailVerified, String name, String picture, String provider) {
    }

    boolean configured();

    /** The identity, or INVALID_TOKEN (forged, expired, or for another Firebase project). */
    Identity verify(String idToken);
}
