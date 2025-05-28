# The Fixed Sampler

This sampler always returns the configuration that enables the specified features. If this configuration contradicts the
constraints of the feature model, the sampler will fail. It is useful for debugging purposes or when you want to
analyze a specific configuration of the software product line.

## Configuration

The fixed sampler is configured using the following option:

- `features`
    - Specifies the sample the sampler returns as an array of configurations. Each configuration is an array of its
      enabled features. All other features are disabled.
    - Optional: no

For example, the fixed sampler could be configured as follows:

```toml
[product.sampler]
name = "fixed"
features = [["MyAwesomeFeature"], ["MyAwesomeFeature", "AnotherFeature"]]
```
