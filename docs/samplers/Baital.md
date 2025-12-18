# The Baital Sampler

This sampler returns a sample by choosing configurations using 
weighted random sampling. Internally, it uses 
[Baital](https://github.com/meelgroup/baital) with CMSGen.
To enable statistical analysis, the internally used random number generator is initialized with a random seed.

## Configuration

The Baital sampler is configured using the following options:

- `sample-size`
  - The number of configurations to generate.
  - Optional: no
- `t`
  - The t-wise coverage to aim for.
  - Optional: no
- `strategy`
  - The strategy for weight generation.
  - Optional: yes
  - Allowed values: 1 - 5
  - Default: 5
- `rounds`
  - The number of rounds for sample generation, the weights are updated between rounds.
  - Optional: yes
  - Default: 10
- `extra-samples`
  - if 10x configurations are to be generated per round, the best are selected.
  - Optional: yes
  - Default: false

For example, the baital sampler could be configured as follows:

```toml
[product.sampler]
name = "baital"
sample-size = 10
t = 2
strategy = 3
rounds = 7
extra-samples = true
```