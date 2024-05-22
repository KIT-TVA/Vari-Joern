# The fixed sampler

This sampler always returns the specified set of features. If the specified configuration contradicts the constraints of
the feature model, the sampler will fail.

## Configuration

The fixed sampler is configured using the following option:

- `features`
    - Specifies the set of features the sampler returns.
    - Optional: no

For example, the sampler could be configured as follows:

```toml
[sampler]
name = "fixed"
features = ["MyAwesomeFeature", "AnotherFeature"]
```
