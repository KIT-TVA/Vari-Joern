# The uniform sampler

This sampler returns a sample by choosing configurations from a uniform distribution.

## Configuration

The t-wise sampler is configured using the following option:

- `sample-size`
    - The number of configurations to generate.
    - Optional: no

For example, the sampler could be configured as follows:

```toml
[sampler]
name = "uniform"
sample-size = 10
```
