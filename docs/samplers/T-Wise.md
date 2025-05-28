# The t-wise Sampler

This sampler returns a sample that achieves t-wise feature interaction coverage. Internally, it uses the YASA
implementation provided by FeatureIDE. To enable statistical analysis, the internally used random number generator is
initialized with a random seed.

## Configuration

The t-wise sampler is configured using the following option:

- `t`
    - The parameter t for t-wise coverage.
    - Optional: no
- `max-samples`
    - The maximum number of configurations to generate.
    - Optional: yes
    - Default: 2_147_483_647

For example, the sampler could be configured as follows:

```toml
[product.sampler]
name = "t-wise"
t = 2
```
