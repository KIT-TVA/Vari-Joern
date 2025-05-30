# The Uniform Sampler

This sampler returns a sample by choosing configurations from a uniform distribution using
[Smarch](https://github.com/jeho-oh/Smarch).

## Configuration

The t-wise sampler is configured using the following option:

- `sample-size`
    - The number of configurations to generate.
    - Optional: no

For example, the uniform sampler could be configured as follows:

```toml
[product.sampler]
name = "uniform"
sample-size = 10
```
