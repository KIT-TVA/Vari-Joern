# The YASA Sampler

This sampler returns a sample that achieves t-wise feature interaction coverage. Internally, it uses the
[YASA](https://dl.acm.org/doi/10.1145/3377024.3377042) implementation provided
by [FeatureIDE](https://featureide.github.io/). To enable statistical analysis,
the internally used random number generator is initialized with a random seed.

## Configuration

The YASA sampler is configured using the following option:

- `t`
    - The parameter t for t-wise feature interaction coverage.
    - Optional: no
- `max-samples`
    - The maximum number of configurations to generate.
    - Optional: yes
    - Default: 2.147.483.647

For example, the sampler could be configured as follows:

```toml
[product.sampler]
name = "yasa"
t = 2
```
