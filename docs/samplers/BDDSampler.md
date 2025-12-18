# The BDD Sampler

This sampler returns a sample by choosing configurations from a
uniform distribution using BDDs.
Internally [BDDSampler](https://github.com/davidfa71/BDDSampler) is used.

## Configuration

The BDD sampler is configured using the following option:

- `sample-size`
  - The number of configurations to generate.
  - Optional: no

For example, the BDD sampler could be configured as follows:

```toml
[product.sampler]
name = "bddsampler"
sample-size = 10
```
