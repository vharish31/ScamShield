/**
 * Firebase Web SDK Service
 * Initializes Firebase Client Authentication when VITE_FIREBASE_* keys are configured.
 * Never imports or exposes Firebase Admin SDK credentials in the browser.
 */

import { initializeApp, getApps } from 'firebase/app';
import {
  getAuth,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
  updateProfile,
  signOut,
  setPersistence,
  browserSessionPersistence,
  inMemoryPersistence,
} from 'firebase/auth';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  ...(import.meta.env.VITE_FIREBASE_STORAGE_BUCKET
    ? { storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET }
    : {}),
  ...(import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID
    ? { messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID }
    : {}),
  ...(import.meta.env.VITE_FIREBASE_APP_ID
    ? { appId: import.meta.env.VITE_FIREBASE_APP_ID }
    : {}),
};

export const isFirebaseConfigured = Boolean(
  firebaseConfig.apiKey && firebaseConfig.projectId
);

let firebaseApp = null;
let auth = null;

if (isFirebaseConfigured) {
  try {
    firebaseApp = getApps().length ? getApps()[0] : initializeApp(firebaseConfig);
    auth = getAuth(firebaseApp);
  } catch (err) {
    console.warn('Firebase Web SDK initialization warning:', err.message);
  }
}

export async function configureAuthPersistence(rememberMe) {
  if (!auth) return;
  try {
    await setPersistence(
      auth,
      rememberMe ? browserSessionPersistence : inMemoryPersistence
    );
  } catch {
    // Fallback to SDK default persistence
  }
}

function mapFirebaseError(err) {
  const code = err?.code || '';
  switch (code) {
    case 'auth/invalid-credential':
    case 'auth/wrong-password':
    case 'auth/user-not-found':
      return new Error('Invalid email or password. Please check your credentials or register an account.');
    case 'auth/email-already-in-use':
      return new Error('An account with this email address already exists. Please sign in instead.');
    case 'auth/weak-password':
      return new Error('Password is too weak. Please choose at least 6 characters.');
    case 'auth/invalid-email':
      return new Error('Please enter a valid email address.');
    case 'auth/too-many-requests':
      return new Error('Too many failed attempts. Please wait a moment and try again.');
    default:
      return err;
  }
}

export async function firebaseLogin(email, password, rememberMe = false) {
  if (!auth) {
    throw new Error(
      'Firebase Web Authentication is not configured in frontend/.env yet.'
    );
  }
  try {
    await configureAuthPersistence(rememberMe);
    const cred = await signInWithEmailAndPassword(auth, email, password);
    const idToken = await cred.user.getIdToken();
    return { user: cred.user, idToken };
  } catch (err) {
    throw mapFirebaseError(err);
  }
}

export async function firebaseRegister(fullName, email, password) {
  if (!auth) {
    throw new Error(
      'Firebase Web Authentication is not configured in frontend/.env yet.'
    );
  }
  try {
    const cred = await createUserWithEmailAndPassword(auth, email, password);
    if (fullName) {
      await updateProfile(cred.user, { displayName: fullName });
    }
    const idToken = await cred.user.getIdToken(true);
    return { user: cred.user, idToken };
  } catch (err) {
    throw mapFirebaseError(err);
  }
}

export async function firebaseResetPassword(email) {
  if (!auth) {
    throw new Error(
      'Firebase Web Authentication is not configured in frontend/.env yet.'
    );
  }
  try {
    await sendPasswordResetEmail(auth, email);
  } catch (err) {
    throw mapFirebaseError(err);
  }
}

export async function firebaseUpdateDisplayName(displayName) {
  if (!auth || !auth.currentUser) {
    throw new Error('No active Firebase Web user session.');
  }
  await updateProfile(auth.currentUser, { displayName });
  return auth.currentUser.getIdToken(true);
}

export async function firebaseLogout() {
  if (auth) {
    await signOut(auth);
  }
}

export { firebaseApp, auth };

