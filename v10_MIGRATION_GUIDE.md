# Migrating to Unleash-Client-Java 10.0.0

This guide highlights the key changes you need to be aware of when upgrading to v10.0.0 of the Unleash client.

## Version guidance

For ongoing updates, prefer v12.

The latest patch releases in v10, v11, and v12 are currently aligned on the same optimized
implementation path.

Early v11 introduced a WASM-engine direction that we later reverted in v11 patch releases.

If you need to stay on a specific major version, make sure you run the latest patch in that major.

## Custom bootstrapping

The Bootstrapping interface now requires an `Optional<String>` to be returned rather than a `String`. If the bootstrapper fails to load the feature set, return an `Optional` of empty.

## `MoreOperations`

`MoreOperations` no longer lists `count` or `countVariant`, these are considered internal APIs and are no longer publicly exposed.

`getFeatureToggleDefinition` no longer returns the complete feature flag definition. Instead, it returns a lightweight Java object that contains the name of the flag, the project that it's bound to, and an optional type parameter that describes the feature flag type in Unleash, such as "experiment" or "killswitch".

## Strategies

The strategy interface has changed to only include the two methods `getName` and `isEnabled`. `isEnabled` now requires both a parameter map and an `UnleashContext`. This only affects users who are implementing custom or fallback strategies.

Built-in strategy classes from `io.getunleash.strategy` (for example
`GradualRolloutRandomStrategy`, `GradualRolloutUserWithIdStrategy`, and
`GradualRolloutSessionIdStrategy`) are no longer part of the Java SDK public API in v10.

These strategies are evaluated internally by Yggdrasil. There is no class-for-class replacement to import for built-ins; use the built-ins via Unleash configuration, and implement `Strategy` only for custom or fallback behavior.

## Events

The following subscriber functions are no longer available: `togglesBackedUp`, `toggleBackupRestored`, and `togglesBootstrapped`. Subscribing to `featuresBackedUp`, `featuresBackupRestored`, and `featuresBootstrapped` respectively serves the same purpose. These subscribers no longer yield events that contain the full feature flag definition, instead, they expose a `getFeatures` method which returns a list of lightweight Java objects containing the feature name, the type of flag, and the project it's bound to.

The `togglesFetched` listener now returns a `ClientFeaturesResponse` event, which has an identical `getFeatures` method instead of the full feature flag definitions.

## Name changes

- `Variant` has been moved to the `io.getunleash.variant` namespace.
- The public interface `IFeatureRepository` has been renamed to `FeatureRepository`.
- The concrete implementor of `FeatureRepository` has been renamed to `FeatureRepositoryImpl`.


## Removal of deprecated APIs

The following public deprecated APIs have been removed:

### Methods on `DefaultUnleash`:
- `deprecatedGetVariant` - This computes a variant with an old hash seed. This is no longer supported.
- `getFeatureToggleDefinition` - `DefaultUnleash.more().getFeatureToggleDefinition()`.
- `getFeatureToggleNames` - Use `DefaultUnleash.more().getFeatureToggleNames()` instead.
- `count` - No longer publicly accessible.

### Methods on `FakeUnleash`:
- `deprecatedGetVariant` - No longer present on the parent interface, removed for consistency.

### Methods on `VariantUtil`:
- `selectDeprecatedVariantHashingAlgo` - Removed since it's no longer required for `deprecatedGetVariant`.

### Other classes and interfaces:

`FeatureToggleRepository` - Use `FeatureRepositoryImpl` instead.

`ToggleRepository` - Use `FeatureRepository` instead.

`HttpToggleFetcher` - Use `HttpFeatureFetcher` instead.

`ToggleFetcher` - Use `FeatureFetcher` instead.

`JsonToggleCollectionDeserializer` - No alternative needed.

`JsonToggleParser` - No alternative needed.

`ToggleBackupHandlerFile` - No alternative needed.
